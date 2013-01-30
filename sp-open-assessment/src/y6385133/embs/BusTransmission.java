package y6385133.embs;

import ptolemy.actor.NoRoomException;
import ptolemy.kernel.util.IllegalActionException;

public class BusTransmission extends Transmission {
	private double startTime;
	private double finishTime;
			
	public BusTransmission(double startTime, Message message, Bus parentBus) {
		super(message, parentBus);
		
		this.startTime  = startTime;
		this.finishTime = startTime + (double) (message.getLength() / parentBus.getBandwidth());
	}
	
	@Override
	public double getFinishTime() {
		return this.finishTime;
	}

	@Override
	public boolean hasFinishedTransmitting(double currentTime) throws NoRoomException, IllegalActionException {
		return currentTime >= this.finishTime;
	}

	@Override
	/**
	 * Notifies the verification analyser that the final message has been
	 * delivered
	 */
	public void endTransmission() throws NoRoomException, IllegalActionException {
		getMessage().markAsTransmitted();
		getOriginBus().processMessage(getMessage());
	}
}
