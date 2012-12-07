package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class SinkB {

	private static Timer tsend;
	private static Timer tstart;
	private static Timer rEnd;

	private static boolean light = true;

	private static byte[] xmit;
	private static long wait;
	static Radio radio = new Radio();
	private static int n = 1; // number of beacons of sync phase - sample only,
								// assessment will use unknown values
	private static int nc;

	private static int t = 1000; // milliseconds between beacons - sample only,
								// assessment will use unknown values

	// settings for sink A
	private static byte channel = 1; // channel 11
	private static byte panid = 0x12;
	private static byte address = 0x12;
	private static boolean inReceivePeriod;

	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		// Set channel
		radio.setChannel((byte) channel);

		// Set the PAN ID and the short address
		radio.setPanId(panid, true);
		radio.setShortAddr(address);

		radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
		
		// Prepare beacon frame with source and destination addressing
		xmit = new byte[12];
		xmit[0] = Radio.FCF_BEACON;
		xmit[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
		Util.set16le(xmit, 3, panid); // destination PAN address
		Util.set16le(xmit, 5, 0xFFFF); // broadcast address
		Util.set16le(xmit, 7, panid); // own PAN address
		Util.set16le(xmit, 9, address); // own short address

		xmit[11] = (byte) n;

		// register delegate for received frames
		radio.setRxHandler(new DevCallback(null) {
			public int invoke(int flags, byte[] data, int len, int info,
					long time) {
				return SinkB.onReceive(flags, data, len, info, time);
			}
		});

		// Setup a periodic timer callback for beacon transmissions
		tsend = new Timer();
		tsend.setCallback(new TimerEvent(null) {
			public void invoke(byte param, long time) {
				SinkB.periodicSend(param, time);
			}
		});

		// Setup a periodic timer callback to restart the protocol
		tstart = new Timer();
		tstart.setCallback(new TimerEvent(null) {
			public void invoke(byte param, long time) {
				SinkB.restart(param, time);
			}
		});

		// Timer to cancel reception period
		rEnd = new Timer();
		rEnd.setCallback(new TimerEvent(null) {
			public void invoke(byte param, long time) {
				SinkB.stopReceivePeriod(param, time);
			}
		});


		// Convert the periodic delay from ms to platform ticks
		wait = Time.toTickSpan(Time.MILLISECS, t);

		// starts the protocol 5 seconds after constructing the assembly
		tstart.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 5)); 
	}

	// Called when a frame is received
	private static int onReceive(int flags, byte[] data, int len, int info,
			long time) {
		if (data == null) {
			radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);

			return 0;
		}

		// Red if in receive period, else yellow
		byte led = (byte) (inReceivePeriod ? 2 : 0);

		// frame received, so blink red LED and log its payload

		LED.setState(led, (byte) (LED.getState(led) == 0 ? 1 : 0));

		if (inReceivePeriod) {
			Logger.appendString(csr.s2b("SinkB.: In Reception: "));
		} else {
			Logger.appendString(csr.s2b("SinkB.: NOT In Reception: "));
		}

		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		return 0;

	}

	protected static void stopReceivePeriod(byte param, long time) {
		inReceivePeriod = false;

		// turn green LED off
		LED.setState((byte) 1, (byte) 0);

		// set alarm to restart protocol
		tstart.setAlarmBySpan(10 * wait);
	}

	// Called on a timer alarm
	public static void periodicSend(byte param, long time) {

		if (nc > 0) {
			// transmit a beacon
			radio.transmit(Device.ASAP | Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
			// program new alarm
			tsend.setAlarmBySpan(wait);
			nc--;
			xmit[11]--;
		} else {
			// turn green LED on
			LED.setState((byte) 1, (byte) 1);
			inReceivePeriod = true;
			rEnd.setAlarmBySpan(wait);
		}
	}

	// Called on a timer alarm, starts the protocol
	public static void restart(byte param, long time) {
		nc = n;
		xmit[11] = (byte) n;
		tsend.setAlarmBySpan(0);

	}

}