package y6385133.embs;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.Director;
import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.RecordToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class HierachialBusActor extends TypedAtomicActor {

	private TypedIOPort busAInput;
	private TypedIOPort busBInput;
	private TypedIOPort output;
	private Parameter bandwidth;
	private Bus busA;
	private Bus busB;
	private Bridge bridge;

	public HierachialBusActor(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException {
		super(container,name);
		
		busAInput = new TypedIOPort(this, "busAInput", true, false);
		busAInput.setMultiport(true);
		busBInput = new TypedIOPort(this, "busBInput", true, false);
		busBInput.setMultiport(true);
		
		output = new TypedIOPort(this, "output", false, true);
		output.setMultiport(true);
		
		bandwidth = new Parameter(this,"bandwidth");
		bandwidth.setExpression("1");
		
		busA = new Bus("A", bandwidth, Arrays.asList(0,1,2,3), busAInput, output, this);
		busB = new Bus("B", bandwidth, Arrays.asList(4,5,6,7), busBInput, output, this);
		
		bridge = new Bridge();
		bridge.addBus(busA);
		bridge.addBus(busB);
	}
	
	public void requestFireAt(double atTime) throws IllegalActionException {
		getDirector().fireAt(this, atTime);
	}
	
	public void requestFireASAP() throws IllegalActionException {
		getDirector().fireAtCurrentTime(this);
	}
	
	public void fire() throws IllegalActionException{
		double currentTime = getDirector().getCurrentTime();
		
		busA.performMaintenance(currentTime);
		busB.performMaintenance(currentTime);
		
		busA.performArbitration(currentTime);
		busB.performArbitration(currentTime);
		
		performTransmission(busA, currentTime);
		performTransmission(busB, currentTime);

		System.out.println("");
	}
	
	private void performTransmission(Bus bus, double currentTime) throws NoRoomException, IllegalActionException {
		double fireAt = 0;
		
		fireAt = bus.performTransmission(currentTime);
		
		if(fireAt > 0) {
			requestFireAt(fireAt);
		}
	}
}
