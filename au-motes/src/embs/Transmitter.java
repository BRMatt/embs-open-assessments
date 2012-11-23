package embs;

import com.ibm.saguaro.system.Radio;

public class Transmitter {

	static private byte initialChannel = 11;
	static private Radio radio = new Radio();
	
	static {
		// Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
        // Set channel 
        radio.setChannel(initialChannel);
        radio.setPanId(initialChannel, true);
        radio.setShortAddr(0x14);
        
	}
}
