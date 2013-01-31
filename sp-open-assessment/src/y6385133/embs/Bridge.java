package y6385133.embs;

import java.util.ArrayList;
import java.util.List;

import ptolemy.actor.NoRoomException;
import ptolemy.kernel.util.IllegalActionException;

public class Bridge {
	List<Bus> busses;
	
	public Bridge() {
		busses = new ArrayList<Bus>();
	}
	
	/**
	 * Register the specified bus on this bridge
	 * @param newBus
	 */
	public void addBus(Bus newBus) {
		newBus.setBridge(this);
		busses.add(newBus);
	}
	
	/**
	 * Asks the arbitrar on the remote bus if the specified message
	 * can be broadcast immediately
	 * 
	 * @param message
	 * @return 
	 */
	public boolean reserveRemoteArbitration(Message message) {
		for (Bus bus : busses) {
			if (bus.isConnectedToProcessor(message.getDestinationProcessor())) {
				return bus.requestTransmissionToken(message);
			}
		}
		
		return false;
	}
}
