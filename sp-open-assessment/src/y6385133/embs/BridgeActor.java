package y6385133.embs;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.RecordToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class BridgeActor extends TypedAtomicActor {

	private TypedIOPort busAInput;
	private TypedIOPort busBInput;
	private TypedIOPort output;
	private Parameter bandwidth;
	private Bus busA;
	private Bus busB;

	public BridgeActor(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException {
		super(container,name);
		
		busAInput = new TypedIOPort(this, "busAInput", true, false);
		busAInput.setMultiport(true);
		busBInput = new TypedIOPort(this, "busBInput", true, false);
		busBInput.setMultiport(true);
		
		output = new TypedIOPort(this, "output", false, true);
		output.setMultiport(true);
		
		bandwidth = new Parameter(this,"bandwidth");
		bandwidth.setExpression("1");
		
		busA = new Bus(Arrays.asList(1,2,3,4), busAInput, output);
		busB = new Bus(Arrays.asList(5,6,7,8), busBInput, output);
	}
	
	public void initialize() {
		
	}
	
	public void fire() throws IllegalActionException{
		
	}
}
