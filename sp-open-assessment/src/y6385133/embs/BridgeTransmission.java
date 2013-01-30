package y6385133.embs;

import java.util.Observable;
import java.util.Observer;

import ptolemy.actor.NoRoomException;
import ptolemy.kernel.util.IllegalActionException;

public class BridgeTransmission extends Transmission implements Observer {
	private boolean transmitting = true;
	private boolean sentToBridge = false;
	
	public BridgeTransmission(Message message, Bus originBus) throws NoRoomException, IllegalActionException {
		super(message, originBus);
		message.addObserver(this);
		
		getOriginBus().
			getBridge().
			lockBusContainingProcessor(getMessage().getDestinationProcessor(), this);
	}

	@Override
	public boolean hasFinishedTransmitting(double currentTime) throws NoRoomException, IllegalActionException {
		return ! transmitting;
	}

	@Override
	public double getFinishTime() {
		return 0;
	}

	@Override
	public void endTransmission() {
		// Don't notify the verification tool, the BusTransmission
		// object in the other bus will notify it
	}

	@Override
	public void update(Observable o, Object arg) {
		Message message = (Message) o;
		
		if (message.hasBeenTransmitted()) {
			this.transmitting = false;
			
			try {
				// As this is a long-running transmission we need to manually tell
				// the bus to re-evaluate and remove this transmission
				this.getOriginBus().getActor().requestFireASAP();
			} catch (IllegalActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
