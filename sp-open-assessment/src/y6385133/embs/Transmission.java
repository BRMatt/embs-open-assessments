package y6385133.embs;

import ptolemy.actor.NoRoomException;
import ptolemy.kernel.util.IllegalActionException;

public abstract class Transmission {
	private Message message;
	private Bus originBus;
	
	
	public Transmission(Message message, Bus originBus) {
		this.message   = message;
		this.originBus = originBus;
	}
	
	public Message getMessage() {
		return this.message;
	}
	
	public Bus getOriginBus() {
		return this.originBus;
	}
	
	public abstract double getFinishTime();
	public abstract boolean hasFinishedTransmitting(double currentTime) throws NoRoomException, IllegalActionException;
	public abstract void endTransmission() throws NoRoomException, IllegalActionException;

	public double getCurrentResponseTime(double currentTime) {
		return currentTime - message.getReleaseTime();
	}
}