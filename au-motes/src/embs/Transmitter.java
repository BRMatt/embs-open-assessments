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
import com.ibm.saguaro.system.csr;

public class Transmitter {

	static final byte sinkAChannel = 0x0;
	static final byte sinkBChannel = 0x1;
	static final byte sinkCChannel = 0x2;
	
	static private Radio radio = new Radio();
	
	/**
	 * The largest sequence 
	 */
	static private int sinkAMaxSequenceNumber = 0x0;
	static private int sinkBMaxSequenceNumber = 0x0;
	static private int sinkCMaxSequenceNumber = 0x0;
	
	/**
	 * The lowest sequence number we have received for each channel
	 */
	static private int sinkAMinSequenceNumber = 0x0;
	static private int sinkBMinSequenceNumber = 0x0;
	static private int sinkCMinSequenceNumber = 0x0;
	
	/**
	 * Our estimated value for the period. Used to schedule the broadcast period
	 */
	static private long sinkAPeriod = 0;
	static private long sinkBPeriod = 0;
	static private long sinkCPeriod = 0;
	
	/**
	 * Set of observed sequence numbers across channels.
	 * Key is the sequence number, the value is the time that the sequence number
	 * was received at
	 */
	static private long[] sinkATimes;
	static private long[] sinkBTimes;
	static private long[] sinkCTimes;
	
	/**
	 * Timer for scheduling when we should start observing the next channel
	 */
	static private Timer switchChannelTimer  = new Timer();
	
	/**
	 * Timers which control when we should start broadcasting on the respective
	 * channels
	 */
	static private Timer sinkABroadcastTimer = new Timer();
	static private Timer sinkBBroadcastTimer = new Timer();
	static private Timer sinkCBroadcastTimer = new Timer();
	
	
	
	/**
	 * Times at which we should stop broadcasting to respective motes
	 */
	static private long sinkABroadcastDeadline = 0;
	static private long sinkBBroadcastDeadline = 0;
	static private long sinkCBroadcastDeadline = 0;
	
	/**
	 * The maximum amount of time we can spend observing a single channel
	 */
	static private long maxChannelObserve = Time.toTickSpan(Time.MILLISECS, 3000);
	
	/**
	 * The id of the LED that has been lit up to indicate a received packet
	 */
	static private byte  blinkLED = (byte) 2;
	static private Timer blinkTimer = new Timer();
	
	static {
		// Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Set channel 
        radio.setChannel(sinkAChannel);
        radio.setPanId(sinkAChannel, true);
        radio.setShortAddr(0x14);
        
     // register delegate for received frames
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int WARN, long time) {
                    return  Transmitter.onReceive(flags, data, len, WARN, time);
                }
            });
        
        switchChannelTimer.setAlarmBySpan(maxChannelObserve);
        switchChannelTimer.setCallback(new TimerEvent(null) {
        	public void invoke(byte param, long time){
                Transmitter.channelSwitchAlert(param, time);
            }
        });
        
        sinkABroadcastTimer.setCallback(new TimerEvent(null){
        	public void invoke(byte param, long time){
                Transmitter.broadcastSinkA(param, time);
            }
        });
        
        sinkBBroadcastTimer.setCallback(new TimerEvent(null){
        	public void invoke(byte param, long time){
                Transmitter.broadcastSinkB(param, time);
            }
        });
        
        sinkCBroadcastTimer.setCallback(new TimerEvent(null){
        	public void invoke(byte param, long time){
                Transmitter.broadcastSinkC(param, time);
            }
        });
        
        blinkTimer.setCallback(new TimerEvent(null) {
        	public void invoke(byte param, long time) {
        		Transmitter.stopLEDBlink(param, time);
        	}
        });
        
        LED.setState((byte) 0x0, (byte) 0x1);
	}

	protected static int onReceive(int flags, byte[] data, int len, int WARN, long time) {
		if (data == null) {
			// Make sure we're always receiving
			radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
			
			return 0;
		}
		
		byte currentChannel = radio.getChannel();
		
		Logger.appendString(csr.s2b("received packet on channel "));
		Logger.appendByte(currentChannel);
		Logger.flush(Mote.WARN);
		Logger.flush(Mote.WARN);
		
		blinkLedForChannelReceive(currentChannel);
		updateChannelTokens(currentChannel, data[6], time);
		
		return 0;
	}

	
	/**
	 * Callback invoked when entering Sink C's receive phase
	 * @param param
	 * @param time
	 */
	protected static void broadcastSinkC(byte param, long time) {
		byte currentChannel = radio.getChannel();
		// TODO Auto-generated method stub
		
		radio.setChannel(currentChannel);
		
	}

	/**
	 * Callback invoked when entering Sink B's receive phase
	 * @param param
	 * @param time
	 */
	protected static void broadcastSinkB(byte param, long time) {
		byte currentChannel = radio.getChannel();
		// TODO Auto-generated method stub
		
		radio.setChannel(currentChannel);
	}

	/**
	 * Callback invoked when entering Sink A's receive phase
	 * @param param
	 * @param time
	 */
	protected static void broadcastSinkA(byte param, long time) {
		byte currentChannel = radio.getChannel();
		// TODO Auto-generated method stub
		 
		radio.setChannel(currentChannel);
	}

	protected static void channelSwitchAlert(byte param, long time) {
		byte currentChannel = (byte) radio.getChannel();
		byte newChannel     = (byte) (currentChannel == sinkCChannel ? sinkAChannel : currentChannel + 1);

		scheduleReceptionPeriod(currentChannel);
		
		Logger.appendString(csr.s2b("Switching from listening on channel "));
		Logger.appendByte(currentChannel);
		Logger.appendString(csr.s2b(" to channel "));
		Logger.appendByte(newChannel);
		Logger.flush(Mote.WARN);
		Logger.flush(Mote.WARN);
		
		radio.setChannel(newChannel);
		LED.setState(currentChannel, (byte) 0x0);
		LED.setState(newChannel, (byte) 0x1);
		
		// Re-schedule observe channel switch
		switchChannelTimer.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 3)); 
	}

	/**
	 * Uses the observed sequence periods to estimate the period and schedule a
	 * broadcast period 
	 * @param currentChannel
	 */
	private static void scheduleReceptionPeriod(byte currentChannel) {
		int maxSequenceNumber        = maxSequenceNumberForChannel(currentChannel);
		int minSequenceNumber        = minSequenceNumberForChannel(currentChannel);
		long sequenceDiff = maxSequenceNumber - minSequenceNumber;
		long totalPeriodDifferences  = 0;
		long estimatedPeriod         = 0;
		long periodsLeft             = Transmitter.abs(1 - minSequenceNumber);
		long timeUntilBroadcastStart = 0;
		
		Logger.appendString(csr.s2b("min/max sequenceNumbers: "));
		Logger.appendInt(minSequenceNumber);
		Logger.appendString(csr.s2b("/"));
		Logger.appendInt(maxSequenceNumber);
		Logger.flush(Mote.WARN);
		
		if (sequenceDiff == 0) {
			return;
		}
		
		// We start at +1 minimum received so that we don't compare one time to 0
		// e.g. for data [time1, time2, time3] we compute (time2 - time1) + (time3 - time2) 
		for(int periodI = (minSequenceNumber + 1); periodI <= maxSequenceNumber; periodI++) {
			totalPeriodDifferences += timeForSequenceNumber(currentChannel, periodI) - timeForSequenceNumber(currentChannel, periodI - 1);
		}
		
		Logger.appendString(csr.s2b("calculated period is: "));
		Logger.appendLong(totalPeriodDifferences);
		Logger.appendString(csr.s2b("/"));
		Logger.appendLong(sequenceDiff);
		Logger.flush(Mote.WARN); 
		
		// minSequenceNumber can only go as low as 1, e.g. in example above difference = 3-1 = 2
		estimatedPeriod = totalPeriodDifferences / sequenceDiff;
		
		// TODO: Shouldn't this take into account the diff between last receive time and now?
		timeUntilBroadcastStart = periodsLeft * estimatedPeriod;
		
		switch(currentChannel) { 
			case sinkAChannel:
				sinkABroadcastTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, timeUntilBroadcastStart));
				break;
			case sinkBChannel:
				sinkBBroadcastTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, timeUntilBroadcastStart));
				break;
			case sinkCChannel:
				sinkCBroadcastTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, timeUntilBroadcastStart));
				break;
		}
	}


	/**
	 * Abstraction for storing a received sequence number in a channel's sequence number history
	 * 
	 * @param channel
	 * @param sequenceNumber
	 * @param timeReceivedAt
	 */
	protected static void updateChannelTokens(byte channel, byte sequenceNumber, long timeReceivedAt) {
		switch (channel) {
			case sinkAChannel:
				sinkATimes[sequenceNumber] = timeReceivedAt;
				sinkAMaxSequenceNumber = Transmitter.max((int) sequenceNumber, sinkAMaxSequenceNumber);
				sinkAMinSequenceNumber = sequenceNumber;
				break;
			case sinkBChannel:
				sinkBTimes[sequenceNumber] = timeReceivedAt;
				sinkBMaxSequenceNumber = Transmitter.max((int) sequenceNumber, sinkBMaxSequenceNumber);
				sinkBMinSequenceNumber = sequenceNumber;
				break;
			case sinkCChannel:
				sinkCTimes[sequenceNumber] = timeReceivedAt;
				sinkCMaxSequenceNumber = Transmitter.max((int) sequenceNumber, sinkCMaxSequenceNumber);
				sinkCMinSequenceNumber = sequenceNumber;
				break;
		}
	}
	
	/**
	 * Reset all channel related tokens for the specified period
	 * @param channel
	 */
	private static void resetChannelTokens(byte channel) {
		switch (channel) {
			case sinkAChannel:
				sinkATimes = null;
				sinkAMaxSequenceNumber = sinkAMinSequenceNumber = 0x0;
				break;
			case sinkBChannel:
				sinkBTimes = null;
				sinkBMaxSequenceNumber = sinkBMinSequenceNumber = 0x0;
				break;
			case sinkCChannel:
				sinkCTimes = null;
				sinkCMaxSequenceNumber = sinkCMinSequenceNumber = 0x0;
				break;
		}
	}
	
	/**
	 * Get the minimum received sequence number for the specified channel
	 * @param currentChannel
	 * @return
	 */
	private static int minSequenceNumberForChannel(byte currentChannel) {
		switch(currentChannel) { 
			case sinkAChannel:
				return sinkAMinSequenceNumber;
			case sinkBChannel:
				return sinkBMinSequenceNumber;
			case sinkCChannel:
				return sinkCMinSequenceNumber;
			default:
				return 0;
		}
	}

	/**
	 * Get the maximum sequence number for the specified channel
	 * @param currentChannel
	 * @return
	 */
	private static int maxSequenceNumberForChannel(byte currentChannel) {
		switch(currentChannel) { 
			case sinkAChannel:
				return sinkAMaxSequenceNumber;
			case sinkBChannel:
				return sinkBMaxSequenceNumber;
			case sinkCChannel:
				return sinkCMaxSequenceNumber;
			default:
				return 0;
		} 
	}

	/**
	 * Get the time that sequence number "sequenceNumber" was received on
	 * the specified channel
	 * @param currentChannel
	 * @param sequenceNumber
	 * @return
	 */
	private static long timeForSequenceNumber(byte currentChannel, int sequenceNumber) {
		switch(currentChannel) { 
			case sinkAChannel:
				return sinkATimes[sequenceNumber];
			case sinkBChannel:
				return sinkBTimes[sequenceNumber];
			case sinkCChannel:
				return sinkCTimes[sequenceNumber];
			default:
				return 0;
		} 
	}
	
	/**
	 * Timer callback for stopping the receive packet led
	 * @param param
	 * @param time
	 */
	protected static void stopLEDBlink(byte param, long time) {
		LED.setState(blinkLED, (byte) 0);
	}
	
	/**
	 * Turns on an LED that indicates we've received a packet
	 * @param currentChannel
	 */
	private static void blinkLedForChannelReceive(byte currentChannel) {
		// TODO Auto-generated method stub
		blinkLED = (byte) (currentChannel == sinkAChannel ? sinkCChannel : currentChannel - 1);
		
		LED.setState(blinkLED, (byte) 1);
		
		blinkTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 250));
	}

	
	private static byte max(byte a, byte b) {
		return a > b ? a : b;
	}
	
	private static int max(int a, int b) {
		return a > b ? a : b;
	}
	
	private static int abs(int val) { 
		return val < 0 ? -val : val;
	}
	
	private static long abs(long val) { 
		return val < 0 ? -val : val;
	}
}
