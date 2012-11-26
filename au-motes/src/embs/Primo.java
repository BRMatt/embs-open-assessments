package embs;

import com.ibm.saguaro.logger.Logger;
import com.ibm.saguaro.system.DevCallback;
import com.ibm.saguaro.system.Device;
import com.ibm.saguaro.system.Mote;
import com.ibm.saguaro.system.Radio;
import com.ibm.saguaro.system.Time;
import com.ibm.saguaro.system.Timer;
import com.ibm.saguaro.system.TimerEvent;
import com.ibm.saguaro.system.csr;

public class Primo {

	static final byte sinkAChannel = 0;
	static final byte sinkBChannel = 1;
	static final byte sinkCChannel = 2;
	
	static private Radio radio = new Radio();
	
	/**
	 * Timer for scheduling when we should start observing the next channel
	 */
	static private Timer switchChannelTimer  = new Timer();
	
	/**
	 * The maximum amount of time we can spend observing a single channel
	 */
	static private long maxChannelObserve = Time.toTickSpan(Time.MILLISECS, 3000);
	
	/**
	 * Set of observed sequence numbers across channels.
	 * Key is the sequence number, the value is the time that the sequence number
	 * was received at
	 */
	static private long[] broadcastTimes = new long[11];
	
	static private int maxSequenceNumber = 0;
	static private int minSequenceNumber = 100;
	static private int receivedSequenceNumbers = 0;
	
	
	static {
		// Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Set channel 
        switchChannel(sinkAChannel);
        
        
     // register delegate for received frames
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int WARN, long time) {
                    return  Primo.onReceive(flags, data, len, WARN, time);
                }
            });
        
        
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}
	
	protected static int onReceive(int flags, byte[] data, int len, int wARN,
			long time) {
		
		if (data == null) { 
			// Make sure we're always receiving
			radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
			
			Logger.appendString(csr.s2b("reset radio"));
			Logger.flush(Mote.WARN);
			
			return 0;
		}
		
		int sequenceNumber = (int) data[11];
		
		broadcastTimes[sequenceNumber] = time;
		maxSequenceNumber = Primo.max(sequenceNumber, maxSequenceNumber);
		minSequenceNumber = Primo.min(sequenceNumber, minSequenceNumber);
		
		Logger.appendString(csr.s2b("Received sequence number: "));
		Logger.appendInt(sequenceNumber);
		Logger.appendString(csr.s2b(" time: "));
		Logger.appendLong(time);
		Logger.appendString(csr.s2b(" min: "));
		Logger.appendInt(minSequenceNumber);
		Logger.appendString(csr.s2b(" max: "));
		Logger.appendInt(maxSequenceNumber);
		Logger.flush(Mote.WARN);

		estimateReceiveTime();
		
		return 0;
	}
	
	static private void estimateReceiveTime() {
		if(receivedSequenceNumbers == 0) {
			return;
		}  

		return;
	}
	
	static private void switchChannel(byte channel) {
		// TODO Auto-generated method stub
		Logger.appendString(csr.s2b("Switching to channel: "));
		Logger.appendByte(channel);
		Logger.flush(Mote.WARN);
		
		radio.stopRx();
		radio.setChannel(channel);
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        radio.setPanId(0x11 + channel, true);
        radio.setShortAddr(0x14);
	}
	
	static private int max(int a, int b) {
		return a > b ? a : b;
	}
	
	static private int min(int a, int b) {
		return a < b ? a : b;
	}
}
