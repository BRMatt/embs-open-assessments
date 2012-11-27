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

	static final byte sinkAChannel = 0;
	static final byte sinkBChannel = 1;
	static final byte sinkCChannel = 2;

	static private Radio radio = new Radio();

	/**
	 * Timer for scheduling when we should start observing the next channel
	 */
	static private Timer switchChannelTimer = new Timer();
	
	static private Timer sinkABroadcastTimer = new Timer();
	static private Timer sinkBBroadcastTimer = new Timer();
	static private Timer sinkCBroadcastTimer = new Timer();

	/**
	 * The maximum amount of time we can spend observing a single channel
	 */
	static private long maxChannelObserve = Time.toTickSpan(Time.MILLISECS,
			3000);

	/**
	 * Set of observed sequence numbers across channels. Key is the sequence
	 * number, the value is the time that the sequence number was received at
	 */
	static private long[] broadcastTimes = new long[11];

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
		radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
	}

	protected static int onReceive(int flags, byte[] data, int len, int wARN,
			long time) {
		if (data == null) {
			// We explicitly control when we enable the radio using timers etc.
			return 0;
		}

		Logger.appendString(csr.s2b("Reading sequence number"));
		Logger.flush(Mote.WARN);
		int sequenceNumber = (int) data[11];

		maxSequenceNumber = Primo.max(sequenceNumber, maxSequenceNumber);
		minSequenceNumber = Primo.min(sequenceNumber, minSequenceNumber);
		
		broadcastTimes[sequenceNumber] = time;
		++receivedSequenceNumbers;
		
		if(successfullyScheduledBroadcast()) {
			switchChannel(nextChannel(radio.getChannel()));
			resetPeriodDetection();
		}

		return 0;
	}
	
	private static void resetPeriodDetection() {
		maxSequenceNumber = 0;
		minSequenceNumber = 100;
		receivedSequenceNumbers = 0;
		broadcastTimes = new long[11];
	}

	protected static void broadcastToC(byte arg0, long time) {
		broadcastToSink(sinkCChannel);
	}

	protected static void broadcastToB(byte arg0, long time) {
		broadcastToSink(sinkBChannel);
	}

	protected static void broadcastToA(byte arg0, long time) {
		broadcastToSink(sinkAChannel);
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
		xmit[11] = (byte) 14;
		
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
		
		switchChannel(originalChannel);
	}

	/**
	 * Tries to schedule a broadcast period to the specified mote
	 * @return
	 */
	static private boolean successfullyScheduledBroadcast() {
		long receiveTime = estimateReceiveTime();
		
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
	static private long estimateReceiveTime() {
		if (receivedSequenceNumbers < 2) {
			return 0;
		}
		
		long totalTicks = 0;
		long avgPeriod  = 0;
		
		for(int i = minSequenceNumber + 1; i <= maxSequenceNumber; ++i) {
			totalTicks += (broadcastTimes[i] - broadcastTimes[i -1]);
		}
		
		avgPeriod = totalTicks / (receivedSequenceNumbers - 1); 
		
		Logger.appendString(csr.s2b("Average period is: "));
		Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, avgPeriod));
		Logger.flush(Mote.WARN);
		
		return broadcastTimes[minSequenceNumber] + abs(avgPeriod *  minSequenceNumber);
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
		Logger.appendString(csr.s2b("Switching from channel: "));
		Logger.appendByte(radio.getChannel());
		Logger.appendString(csr.s2b(" to: "));
		Logger.appendByte(channel);
		Logger.flush(Mote.WARN);

		LED.setState(radio.getChannel(), (byte) 0x0);
		LED.setState(channel, (byte) 1);
		
		if(stopStartRadio) {
			radio.stopRx();
			Logger.appendString(csr.s2b("Radio stoppped"));
			Logger.flush(Mote.WARN);
		}
		
		radio.setChannel(channel);
		
		radio.setPanId((0x11 + channel), false);
		
		if(stopStartRadio) {
			radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
			Logger.appendString(csr.s2b("Receiving started"));
			Logger.flush(Mote.WARN);
		}
	}

	static private byte nextChannel(byte currentChannel) {
		return currentChannel == sinkCChannel ? sinkAChannel : ++currentChannel;
	}
	
	static private byte previousChannel(byte currentChannel) {
		return currentChannel == sinkAChannel ? sinkCChannel : --currentChannel;
	}
	
	static private int max(int a, int b) {
		return a > b ? a : b;
	}

	static private int min(int a, int b) {
		return a < b ? a : b;
	}
	
	static private int abs(int a) {
		return a < 0 ? - a : a;
	}
	
	static private long abs(long a) {
		return a < 0 ? - a : a;
	}
}
