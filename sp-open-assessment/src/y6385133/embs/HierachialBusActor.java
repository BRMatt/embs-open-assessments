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
		
		busA = new Bus("A", bandwidth, Arrays.asList(0,1,2,3), busAInput, output);
		busB = new Bus("B", bandwidth, Arrays.asList(4,5,6,7), busBInput, output);
		
		bridge = new Bridge();
		bridge.addBus(busA);
		bridge.addBus(busB);
	}
	
	public void fire() throws IllegalActionException{
		fireBus(busA);
		fireBus(busB);
	}
	
	private void fireBus(Bus currentBus) throws NoRoomException, IllegalActionException {
		Director director = getDirector();
		
		double nextFireAt = currentBus.process(director.getCurrentTime());
		
		if(nextFireAt > 0) {
			director.fireAt(this, nextFireAt);
		}
	}
}
