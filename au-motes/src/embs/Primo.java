package embs;

import com.ibm.saguaro.logger.Logger;
import com.ibm.saguaro.system.DevCallback;
import com.ibm.saguaro.system.Device;
import com.ibm.saguaro.system.LED;
import com.ibm.saguaro.system.Mote;
import com.ibm.saguaro.system.Radio;
import com.ibm.saguaro.system.Time;
import com.ibm.saguaro.system.Timer;
import com.ibm.saguaro.system.TimerEvent;
import com.ibm.saguaro.system.Util;
import com.ibm.saguaro.system.csr;

public class Primo {

	/**
	 * Useful constants for referring to the channels each sink is on
	 */
	static final byte sinkAChannel = 0;
	static final byte sinkBChannel = 1;
	static final byte sinkCChannel = 2;

	/**
	 * Access to our radio interface
	 */
	static private Radio radio = new Radio();

	/**
	 * Timer for scheduling when we should start observing the next channel
	 */
	static private Timer stopObservingTimer  = new Timer();
	
	/**
	 * Timers that indicate the start of a sink's next reception phase
	 */
	static private Timer sinkABroadcastTimer = new Timer();
	static private Timer sinkBBroadcastTimer = new Timer();
	static private Timer sinkCBroadcastTimer = new Timer();
	
	/**
	 * Timers that are used to observe the maximum
	 */
	static private Timer sinkAMaxObserverTimer = new Timer();
	static private Timer sinkBMaxObserverTimer = new Timer();
	static private Timer sinkCMaxObserverTimer = new Timer();
	
	/**
	 * A collection of all the periods we have estimated
	 */
	static private long[] sinkPeriod    =  {0, 0, 0};
	
	/**
	 * The number of periods we've discovered
	 * Used to determine if the radio can be put into sleep mode
	 */
	static private int periodsFound = 0;
	
	/**
	 * The maximum known sequence numbers for all sinks 
	 */
	static private int[] sinkMaxNumbers = {0, 0, 0};
	/**
	 * By default try to transmit at maximum power
	 */
	static private int[] sinkPowers = {Radio.TXMODE_POWER_MAX, Radio.TXMODE_POWER_MAX, Radio.TXMODE_POWER_MAX};

	static private int[]  sinkCalMinNumber     = {100, 100, 100};
	static private long[] sinkCalMinNumberTime = new long[3];
	static private int[]  sinkCalMaxNumber     = {0, 0 , 0};
	static private long[] sinkCalMaxNumberTime = new long[3];
	
	/**
	 * The maximum amount of time we should spend observing a single channel
	 */
	static private long maxChannelObserve = Time.toTickSpan(Time.MILLISECS, 3250);
	
	/**
	 * An arbitrary offset into the reception period that we should try to transmit at
	 */
	static private final long receptionPeriodFudgeFactor = Time.toTickSpan(Time.MILLISECS, 200);
	static private final long broadcastTimeFudgeFactor = receptionPeriodFudgeFactor + Time.toTickSpan(Time.MILLISECS, 100);

	private static boolean radioIsOn;
	
	
	static {
		LED.setState((byte) 0, (byte) 1); 
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		// This value controls filtering of incoming frames. 
		// The broadcast value makes the radio stack accept all frames matching the 
		// PAN address no matter what the value of the mote short address in the radio frame.
		// This call can only be performed if the radio API is in state SLEEP or ACTIVE.
		radio.setShortAddr(Radio.SADDR_BROADCAST);

		// Set channel
		switchChannel(sinkAChannel, false);

		// register delegate for received frames
		radio.setRxHandler(new DevCallback(null) {
			public int invoke(int flags, byte[] data, int len, int info,
					long time) {
				return Primo.onReceive(flags, data, len, info, time);
			}
		});

		sinkABroadcastTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.broadcastToA(arg0, time);
			}
		});
		
		sinkBBroadcastTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.broadcastToB(arg0, time);
			}
		});
		
		sinkCBroadcastTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.broadcastToC(arg0, time);
			}
		});
		sinkAMaxObserverTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.observeMaxForA(arg0, time);
			}
		});
		sinkBMaxObserverTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.observeMaxForB(arg0, time);
			}
		});
		sinkCMaxObserverTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.observeMaxForC(arg0, time);
			}
		});
		stopObservingTimer.setCallback(new TimerEvent(null) {
			public void invoke(byte arg0, long time) {
				Primo.stopObserving(arg0, time);
			}
		});
		stopObservingTimer.setAlarmBySpan(maxChannelObserve);
	}

	protected static int onReceive(int flags, byte[] data, int len, int info,
			long time) {
		if (data == null) {
			// We explicitly control when we enable the radio using timers etc.
			return 0;
		}

		LED.setState((byte) 1, (byte) (LED.getState((byte) 1) == 0 ? 1 : 0));
		
		byte currentSink    = (byte) radio.getChannel();
		int  sequenceNumber = (int) data[11];
		// RSSI is on a scale of 0-255, transmit power is on a scale of 0-63
		// We divide by 256 (*2^-8), then multiply by 64 (2^6) to get the transmit power
		// Then we bit shift it up into the correct position (The top 6 bits)
		// Transmit power = RSSI * 2^-8 * 2^6 * 2^10 = RSSI * 2^8
		int  newPowerLevel  = (int) ((info & 0xFF) << 7) & Radio.TXMODE_POWER_MASK;
		
		/*Logger.appendString(csr.s2b("Channel "));
		Logger.appendByte(radio.getChannel());
		Logger.appendString(csr.s2b(" (CP "));
		Logger.appendLong(sinkPeriod[currentSink]);
		Logger.appendString(csr.s2b(" MN "));
		Logger.appendLong(sinkMaxNumbers[currentSink]);
		Logger.appendString(csr.s2b("): "));
		Logger.appendInt(sequenceNumber);
		Logger.appendString(csr.s2b(" "));*/
		
		if(sinkPeriod[currentSink] <= 0) {
			handleCallibration(time, currentSink, sequenceNumber);
		} else {
			// Never cross the streams...
			if(sequenceNumber > sinkMaxNumbers[currentSink]) {
				sinkMaxNumbers[currentSink] = sequenceNumber;
			}
			
			scheduledBroadcast(time + (sinkPeriod[currentSink] * sequenceNumber) + receptionPeriodFudgeFactor);
		}
		
		LED.setState((byte) 0, (byte) (periodsFound == 3 ? 0 : 1));
		
		/*Logger.flush(Mote.WARN);
		Logger.appendString(csr.s2b("Changing power for chanel "));
		Logger.appendByte(currentSink);
		Logger.appendString(csr.s2b(" to "));
		Logger.appendInt(newPowerLevel);
		Logger.appendString(csr.s2b(" from "));
		Logger.appendInt(sinkPowers[currentSink]);
		Logger.flush(Mote.WARN);*/
		sinkPowers[currentSink] = newPowerLevel;
		
		return 0;
	}
	
	
	protected static void observeMaxForB(byte arg0, long time) {
		switchChannel(sinkBChannel);
	}


	protected static void observeMaxForC(byte arg0, long time) {
		switchChannel(sinkCChannel);
	}


	protected static void observeMaxForA(byte arg0, long time) {
		switchChannel(sinkAChannel);
	}


	/**
	 * Attempts to calibrate the sink on the current channel based on the observed sequence numbers
	 * 
	 * @param time           The time this sequence number was observed at
	 * @param currentSink    The id of the current sink (and its channel)
	 * @param sequenceNumber The received sequence number
	 */
	private static void handleCallibration(long time, byte currentSink, int sequenceNumber) {
		long estimatedPeriod       = 0;
		long receivePeriodStartsAt = 0;
		
		if(sequenceNumber == 1 && sinkCalMaxNumber[currentSink] == 1) {
			// n == 1
			long beaconTimeDiff = (time - sinkCalMaxNumberTime[currentSink]);
			
			sinkCalMinNumber[currentSink] = 1;
			sinkCalMinNumberTime[currentSink] = time;
			
			estimatedPeriod = (beaconTimeDiff / 12);
			
			Logger.appendString(csr.s2b("n=1"));
		
		} else if (sinkCalMaxNumber[currentSink] < sequenceNumber) {
			// Either this is the first number we've observed, or we've missed the reception
			// phase and looped back round to the head of the sequence
			sinkCalMaxNumber[currentSink]     = sequenceNumber;
			sinkCalMaxNumberTime[currentSink] = time;
			sinkCalMinNumber[currentSink]     = 100;
			sinkCalMinNumberTime[currentSink] = 0;
			
			Logger.appendString(csr.s2b("M<s"));
		} else {
			
			if(sinkCalMaxNumberTime[currentSink] > 0) {
				sinkCalMinNumberTime[currentSink] = time;
				sinkCalMinNumber[currentSink]     = sequenceNumber;
			} else {
				sinkCalMaxNumberTime[currentSink] = time;
				sinkCalMaxNumber[currentSink]     = sequenceNumber;
			}

			estimatedPeriod = estimatePeriodFromSequence(currentSink);
			
			Logger.appendString(csr.s2b("   "));
		}
		

		sinkPeriod[currentSink] = estimatedPeriod;
		
		if(sinkCalMaxNumber[currentSink] > sinkMaxNumbers[currentSink]) {
			sinkMaxNumbers[currentSink] = sinkCalMaxNumber[currentSink];
		}
		
		if(estimatedPeriod > 0) {
			receivePeriodStartsAt = calculateBroadcastTime(time, estimatedPeriod, sinkCalMinNumber[currentSink]);
		}
		
		/*Logger.appendString(csr.s2b(" Period: "));
		Logger.appendLong(estimatedPeriod);
		Logger.appendString(csr.s2b("("));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, estimatedPeriod));
		Logger.appendString(csr.s2b(") "));*/
		
		if(scheduledBroadcast(receivePeriodStartsAt)) {
			/*Logger.appendString(csr.s2b(" // Scheduled at: "));
			Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, receivePeriodStartsAt));
			Logger.flush(Mote.WARN);*/
			++periodsFound;
			switchToSinkWithoutPeriod();
		}
	}
	
	/**
	 * Calculates the time that we should broadcast
	 * @param baseTime
	 * @param periodLength TODO
	 * @param periodsUntilReception
	 * @return
	 */
	private static long calculateBroadcastTime(long baseTime, long periodLength, int periodsUntilReception) {
		if (periodLength <= 0) {
			return 0;
		}
		
		return baseTime + (periodLength * periodsUntilReception) + receptionPeriodFudgeFactor;
	}
	
	/**
	 * Chooses the next channel that needs to be observed to determine a 
	 * sink's period.
	 * 
	 * The criteria for the next channel is that its period must not have been determined
	 * 
	 * 
	 * @return False if all channels have had their periods determined, else True 
	 */
	private static boolean switchToSinkWithoutPeriod() {
		if (periodsFound == 3) {
			return false;
		}
		
		byte currentChannel = radio.getChannel();
		byte nextChannel    = currentChannel;
		
		do {
			nextChannel = (byte) (nextChannel == sinkCChannel ? sinkAChannel : nextChannel + 1);
		} while(nextChannel != currentChannel && sinkPeriod[nextChannel] > 0);
		
		
		switchChannel(nextChannel);
		stopObservingTimer.setAlarmBySpan(maxChannelObserve);
		
		return true;
	}
	
	/**
	 * Don't stick around on a channel
	 * @param arg0
	 * @param time
	 */
	protected static void stopObserving(byte arg0, long time) {
		/*Logger.appendString(csr.s2b("Time to stop observing channel "));
		Logger.appendByte(radio.getChannel());
		Logger.flush(Mote.WARN);*/
		switchToSinkWithoutPeriod();
	}

	protected static void broadcastToC(byte arg0, long time) {
		broadcastToSink(sinkCChannel);
		reschedule(sinkCChannel, time);
	}

	protected static void broadcastToB(byte arg0, long time) {
		broadcastToSink(sinkBChannel);
		reschedule(sinkBChannel, time);
	}

	protected static void broadcastToA(byte arg0, long time) {
		broadcastToSink(sinkAChannel);
		reschedule(sinkAChannel, time);
	}
	
	private static void broadcastToSink(byte broadcastChannel) {
		byte originalChannel = radio.getChannel();
		switchChannel(broadcastChannel);
		
		// Prepare beacon frame with source and destination addressing
        byte[] xmit = new byte[12];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
        Util.set16le(xmit, 3, radio.getPanId()); // destination PAN address 
        Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
        Util.set16le(xmit, 7, radio.getPanId()); // own PAN address 
        Util.set16le(xmit, 9, 0x14); // own short address 
		xmit[11] = (byte) 8;
		
		Logger.appendString(csr.s2b("Transmitting "));
		Logger.appendByte(broadcastChannel);
		Logger.flush(Mote.WARN);
		
		radio.transmit(Device.ASAP|sinkPowers[broadcastChannel], xmit, 0, 12, 0);
		LED.setState((byte) 2, (byte) (LED.getState((byte) 2) == 0 ? 1 : 0)); 
		switchChannel(originalChannel);
	}
	
	private static void reschedule(byte channel, long receiveTime) {
		long period = sinkPeriod[channel];
		
		long startSequenceTime    = receiveTime + (11 * period);
		long nextReceptionPeriod  = startSequenceTime + (sinkMaxNumbers[channel] * period);
		long broadcastObserveTime = (nextReceptionPeriod - period - broadcastTimeFudgeFactor);
		
		/*Logger.appendString(csr.s2b("Rescheduling channel "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" broadcast for "));
		Logger.appendLong(nextReceptionPeriod);
		Logger.appendString(csr.s2b("("));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, nextReceptionPeriod));
		Logger.appendString(csr.s2b(") estimated max sequence#: "));
		Logger.appendInt(sinkMaxNumbers[channel]);
		Logger.appendString(csr.s2b("*"));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, sinkPeriod[channel]));
		Logger.appendString(csr.s2b(" observe at "));
		Logger.appendLong(broadcastObserveTime);
		Logger.appendString(csr.s2b("("));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, broadcastObserveTime));
		Logger.appendString(csr.s2b(")"));
		Logger.flush(Mote.WARN);*/
		
		switch(channel) {
		case sinkAChannel:
			sinkAMaxObserverTimer.setAlarmTime(broadcastObserveTime);
			sinkABroadcastTimer.setAlarmTime(nextReceptionPeriod);
			break;
		case sinkBChannel:
			sinkBMaxObserverTimer.setAlarmTime(broadcastObserveTime);
			sinkBBroadcastTimer.setAlarmTime(nextReceptionPeriod);
			break;
		case sinkCChannel:
			sinkCMaxObserverTimer.setAlarmTime(broadcastObserveTime);
			sinkCBroadcastTimer.setAlarmTime(nextReceptionPeriod);
		}
		
		// We've discovered everything we need to know, don't need to "observe" channels
		// until prompted to by timers
		/*if(periodsFound >= 3) {
			Logger.appendString(csr.s2b("Stopping radio"));
			Logger.flush(Mote.WARN);
			LED.setState((byte) sinkAChannel, (byte) 0x0);
			LED.setState((byte) sinkBChannel, (byte) 0x0);
			LED.setState((byte) sinkCChannel, (byte) 0x0);
			radio.setState(Radio.S_STDBY);
		}*/
	}

	/**
	 * Tries to schedule a broadcast period to the specified mote
	 * @param estimatedPeriod 
	 * @param The estimated time of the sink's receive period
	 * @return
	 */
	static private boolean scheduledBroadcast(long receiveTime) {
		if (receiveTime == 0) {
			return false;
		}
		
		switch(radio.getChannel()) {
			case sinkAChannel:
				sinkABroadcastTimer.setAlarmTime(receiveTime);
				break;
			case sinkBChannel:
				sinkBBroadcastTimer.setAlarmTime(receiveTime);
				break;
			case sinkCChannel:
				sinkCBroadcastTimer.setAlarmTime(receiveTime);
				break;
		}
		
		return true;
	}
	
	/**
	 * Estimates the start of the receive period based on the observed payloads
	 * @return
	 */
	static private long estimatePeriodFromSequence(byte sink) {		
		if (sinkCalMinNumber[sink] == 0 || sinkCalMaxNumber[sink] == 0) {
			return 0;
		}
		
		long tickDiff = sinkCalMinNumberTime[sink] - sinkCalMaxNumberTime[sink];
		long seqDiff  = sinkCalMaxNumber[sink]     - sinkCalMinNumber[sink];

		// If we've managed to jump to another cycle, take the reception + sleep phase into account
		if(seqDiff < 1) {
			/*Logger.appendString(csr.s2b("SOMETHING HAS GONE TERRIBLY WRONG"));
			Logger.flush(Mote.WARN);*/
			
			return 0;
		}

		return tickDiff / seqDiff;
	}

	/**
	 * Override for switching channel when you want to stop/start the radio while
	 * switching
	 * @param channel
	 */
	static private void switchChannel(byte channel) {
		switchChannel(channel, true);
	}
	
	/**
	 * Switch channel and specify whether you want to turn the radio off while switching
	 * @param channel
	 * @param stopStartRadio
	 */
	static private void switchChannel(byte channel, boolean stopRadio) {
		byte currentChannel = radio.getChannel();
		
		if (currentChannel == channel && radioIsOn) {
			/*Logger.appendString(csr.s2b("Already on channel "));
			Logger.appendByte(channel);
			Logger.appendString(csr.s2b(" No need to switch back"));
			Logger.flush(Mote.WARN);*/
			return;
		}
		
		/*Logger.appendString(csr.s2b("Switching from channel "));
		Logger.appendByte(currentChannel);
		Logger.appendString(csr.s2b(" to "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" -- "));
		Logger.appendInt(radio.getState());
		Logger.flush(Mote.WARN);*/
		
		
		if(stopRadio) {
			radioOff();
		}
		
		radio.setChannel(channel);
		
		radio.setPanId((0x11 + channel), false);

		
		radioOn();
	}
	
	static private void radioOn() {
		radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
		radioIsOn = true;
	}
	
	static private void radioOff() {
		radio.stopRx();
		radioIsOn = false;
	}
}
