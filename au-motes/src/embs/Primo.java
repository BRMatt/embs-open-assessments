package embs;

import java.nio.channels.Pipe.SinkChannel;

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
	static private long[] sinkPeriod    = new long[3];
	
	/**
	 * The maximum known sequence numbers for all sinks 
	 */
	static private int[]  sinkConfirmedMaxNumbers = new int[3];
	
	/**
	 * The maximum amount of time we should spend observing a single channel
	 */
	static private long maxChannelObserve = Time.toTickSpan(Time.MILLISECS, 3500);

	/**
	 * Set of observed sequence numbers across channels. Key is the sequence
	 * number, the value is the time that the sequence number was received at
	 */
	static private long[] broadcastTimes = new long[11];

	/**
	 * Values for working out the period for the channel currently being observed 
	 * Used to try and estimate the period of a channel
	 */
	static private int maxSequenceNumber = 0;
	static private int minSequenceNumber = 100;
	static private int receivedSequenceNumbers = 0;
	
	
	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		// This value controls filtering of incomming frames. 
		// The broadcast value makes the radio stack accept all frames matching the 
		// PAN address no matter what the value of the mote short address in the radio frame.
		// This call can only be performed if the radio API is in state SLEEP or ACTIVE.
		radio.setShortAddr(Radio.SADDR_BROADCAST);

		// Set channel
		switchChannel(sinkAChannel, false);

		// register delegate for received frames
		radio.setRxHandler(new DevCallback(null) {
			public int invoke(int flags, byte[] data, int len, int WARN,
					long time) {
				return Primo.onReceive(flags, data, len, WARN, time);
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
		
		radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
	}

	protected static int onReceive(int flags, byte[] data, int len, int wARN,
			long time) {
		if (data == null) {
			// We explicitly control when we enable the radio using timers etc.
			return 0;
		}

		byte currentSink    = (byte) radio.getChannel();
		int  sequenceNumber = (int) data[11];
		
		Logger.appendString(csr.s2b("Channel "));
		Logger.appendByte(radio.getChannel());
		Logger.appendString(csr.s2b(": "));
		
		if(sinkPeriod[currentSink] <= 0) {
			handleCallibration(time, currentSink, sequenceNumber);
		} else {
			// Never cross the streams...
			if(sequenceNumber > sinkConfirmedMaxNumbers[currentSink]) {
				sinkConfirmedMaxNumbers[currentSink] = sequenceNumber;
			}
			
			scheduledBroadcast(time + (sinkPeriod[currentSink] * sequenceNumber));
		}
		

		Logger.flush(Mote.WARN);
		
		return 0;
	}
	
	
	protected static void observeMaxForB(byte arg0, long time) {
		switchChannel(sinkBChannel);
		resetPeriodDetection();
	}


	protected static void observeMaxForC(byte arg0, long time) {
		switchChannel(sinkCChannel);
		resetPeriodDetection();
	}


	protected static void observeMaxForA(byte arg0, long time) {
		switchChannel(sinkAChannel);
		resetPeriodDetection();
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
		
		Logger.appendInt(sequenceNumber);
		Logger.appendString(csr.s2b(" "));
		
		
		// n == 1 and we haven't timed out while watching this
		if(sequenceNumber == 1 && maxSequenceNumber == 1) {
			long beaconTimeDiff = (time - broadcastTimes[sequenceNumber]);
			
			estimatedPeriod = (beaconTimeDiff / 12);
			receivePeriodStartsAt = time + estimatedPeriod;
			
			Logger.appendString(csr.s2b("N=1"));
		} else {
			maxSequenceNumber = sequenceNumber > maxSequenceNumber ? sequenceNumber : maxSequenceNumber;
			minSequenceNumber = sequenceNumber < minSequenceNumber ? sequenceNumber : minSequenceNumber;

			broadcastTimes[sequenceNumber] = time;
			++receivedSequenceNumbers;

			estimatedPeriod = estimatePeriodFromSequence();
			
			if(estimatedPeriod > 0) {
				receivePeriodStartsAt = broadcastTimes[minSequenceNumber] + (estimatedPeriod *  minSequenceNumber);
			}
			
			Logger.appendString(csr.s2b("   "));
		}
		

		sinkPeriod[currentSink] = estimatedPeriod;
		sinkConfirmedMaxNumbers[currentSink] = maxSequenceNumber;
		
		Logger.appendString(csr.s2b(" Period: "));
		Logger.appendLong(estimatedPeriod);
		Logger.appendString(csr.s2b("("));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, estimatedPeriod));
		Logger.appendString(csr.s2b(") "));
		
		if(scheduledBroadcast(receivePeriodStartsAt)) {
			Logger.appendString(csr.s2b(" // Scheduled at: "));
			Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, receivePeriodStartsAt));
			Logger.flush(Mote.WARN);
			observeNextChannel();
		}
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
	private static boolean observeNextChannel() {
		byte currentChannel = radio.getChannel();
		byte nextChannel    = currentChannel;
		
		do {
			nextChannel = (byte) (nextChannel == sinkCChannel ? sinkAChannel : nextChannel + 1);
		} while(nextChannel != currentChannel && sinkPeriod[nextChannel] > 0);
		
		if (nextChannel != currentChannel) {
			switchChannel(nextChannel);
			resetPeriodDetection();
			//stopObservingTimer.setAlarmBySpan(maxChannelObserve);
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Don't stick around on a channel
	 * @param arg0
	 * @param time
	 */
	protected static void stopObserving(byte arg0, long time) {
		observeNextChannel();
	}

	private static void resetPeriodDetection() {
		maxSequenceNumber = 0;
		minSequenceNumber = 100;
		receivedSequenceNumbers = 0;
		broadcastTimes = new long[11];
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
		
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
		
		switchChannel(originalChannel);
	}
	
	private static void reschedule(byte channel, long receiveTime) {
		long period = sinkPeriod[channel];
		
		long startSequenceTime    = receiveTime + (11 * period);
		long nextReceptionPeriod  = startSequenceTime + (sinkConfirmedMaxNumbers[channel] * period);
		long broadcastObserveTime = (nextReceptionPeriod - period - (period / 3));
		
		Logger.appendString(csr.s2b("Rescheduling channel "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" broadcast for "));
		Logger.appendLong(nextReceptionPeriod);
		Logger.appendString(csr.s2b("("));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, nextReceptionPeriod));
		Logger.appendString(csr.s2b(") estimated max sequence#: "));
		Logger.appendInt(sinkConfirmedMaxNumbers[channel]);
		Logger.appendString(csr.s2b("*"));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, sinkPeriod[channel]));
		Logger.flush(Mote.WARN);
		
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
	static private long estimatePeriodFromSequence() {
		if (receivedSequenceNumbers < 2) {
			return 0;
		}
		
		long totalTicks = 0;
		
		for(int i = maxSequenceNumber - 1; i >= minSequenceNumber; --i) {
			totalTicks += broadcastTimes[i] - broadcastTimes[i + 1];
		}
		
		return totalTicks / (receivedSequenceNumbers - 1);
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
	static private void switchChannel(byte channel, boolean stopStartRadio) {
		byte currentChannel = radio.getChannel();
		
		if (currentChannel == channel) {
			Logger.appendString(csr.s2b("Already on channel "));
			Logger.appendByte(channel);
			Logger.appendString(csr.s2b(" No need to switch back"));
			Logger.flush(Mote.WARN);
			return;
		}
		
		Logger.appendString(csr.s2b("Switching from channel "));
		Logger.appendByte(currentChannel);
		Logger.appendString(csr.s2b(" to "));
		Logger.appendByte(channel);
		Logger.flush(Mote.WARN);
		
		LED.setState(currentChannel, (byte) 0x0);
		LED.setState(channel, (byte) 1);
		
		if(stopStartRadio) {
			radio.stopRx();
		}
		
		radio.setChannel(channel);
		
		radio.setPanId((0x11 + channel), false);
		
		if(stopStartRadio) {
			radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
		}
	}
}
