package y6385133.embs;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class Bridge extends TypedAtomicActor {
	
	/**
	 * Using separate ports for input/output to avoid confusion
	 * with multiple types of wire going to the same port
	 */
	protected TypedIOPort[] busInputs;
	protected TypedIOPort[] busOutputs;;
	
	public Bridge(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
		super(container,name);
		
		for (int i = 0; i < 2; ++i) {
			busInputs[i] = new TypedIOPort(this, "bus"+i+"Input", true, false);
			busInputs[i].setMultiport(true);
			
			busOutputs[i] = new TypedIOPort(this, "bus"+i+"Output", false, true);
			busOutputs[i].setMultiport(true);
		}		
	}
	
	public void fire() throws IllegalActionException {
		for (int i=0; i < 2; ++i) {
			TypedIOPort currentInput  = busInputs[i];
			TypedIOPort currentOutput = busOutputs[i];
			
			for (int c = 0, numChannels = currentInput.getWidth(); c < numChannels; ++c) {
				if (currentInput.hasToken(c)) {
					currentOutput.send(c, currentInput.get(c));
				}
			}
		}
	}
}
