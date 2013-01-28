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
	
	public void addBus(Bus newBus) {
		newBus.setBridge(this);
		busses.add(newBus);
	}
	
	public boolean lockBusContainingProcessor(int processor, Transmission com) throws NoRoomException, IllegalActionException {
		for (Bus bus : busses) {
			if (bus.isConnectedToProcessor(processor)) {
				bus.enqueue(com.getMessage());
				
				return true;
			}
		}
		
		return false;
	}
}
