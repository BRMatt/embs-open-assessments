package y6385133.embs;

public class BridgeTransmission extends Transmission {
	private boolean transmitting = true;
	
	public BridgeTransmission(Message message, Bus originBus) {
		super(message, originBus);
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

}
