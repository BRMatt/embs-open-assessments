package y6385133.embs;

import java.util.Observable;
import java.util.Observer;

import ptolemy.actor.NoRoomException;
import ptolemy.kernel.util.IllegalActionException;

public class BridgeTransmission extends Transmission implements Observer {
	private boolean transmitting = true;
	
	public BridgeTransmission(Message message, Bus originBus) throws NoRoomException, IllegalActionException {
		super(message, originBus);
		message.addObserver(this);
		originBus.getBridge().lockBusContainingProcessor(message.getDestinationProcessor(), this);
	}

	@Override
	public boolean hasFinishedTransmitting(double currentTime) {
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
		}
	}

}
