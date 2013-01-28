package y6385133.embs;

import java.util.ArrayList;
import java.util.List;

public class Bridge {
	List<Bus> busses;
	
	public Bridge() {
		busses = new ArrayList<Bus>();
	}
	
	public void addBus(Bus newBus) {
		busses.add(newBus);
	}
	
	public void lockBusContainingProcessor(int processor, Communication com) {
		for (Bus bus : busses) {
			if (bus.isConnectedToProcessor(processor)) {
				bus.enqueue(com);
			}
		}
	}
}
