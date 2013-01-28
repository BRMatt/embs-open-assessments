package lsi.edi;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class Verification extends TypedAtomicActor {

	protected TypedIOPort input;
	protected TypedIOPort output;
	
	public Verification(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {

		super(container, name);
		
		input = new TypedIOPort(this, "input", true, false);
		input.setMultiport(true);
		output = new TypedIOPort(this, "output", false, true);
	}
	
	public void fire() throws IllegalActionException{
		
		//reads tokens from all channels connected to the input port
		for(int i=0;i<input.getWidth();i++){
			if(input.hasToken(i)){
				
				//for each token, extract the id, release time and period
				
				RecordToken packet = (RecordToken)input.get(i);

				int id =  ((IntToken)packet.get("id")).intValue();
				double release = ((DoubleToken)packet.get("releasetime")).doubleValue();
				double commfinish = getDirector().getCurrentTime();
				double period = ((DoubleToken)packet.get("period")).doubleValue();
				
				//calculate the end-to-end latency
				double totalresponsetime = commfinish - release;
				String s="task "+id+" ---";

				//packs the information on a StringToken and outputs it
				s = s+ "resp time: "+totalresponsetime+" --- period: "+period;
				StringToken st = new StringToken(s);
				output.send(0,st);
				
			}
			
		}
		
	}

}
