package y6385133.embs;

import java.util.Observable;

import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;

/**
 * Encapsulation of a message being sent from one task to another
 * @author mb798
 *
 */
class Message extends Observable {
	
	
	/**
	 * The raw token that was taken off the bus
	 */
	private RecordToken token;
	
	/**
	 * The numerical ID of the processor this message came from
	 */
	private int sourceProcessor;
	
	/**
	 * Numerical ID of the processor this message is destined for
	 */
	private int destinationProcessor;
	
	/**
	 * State flag
	 */
	private boolean hasBeenTransmitted = false;
	
	/**
	 * The raw token that holds the "message"
	 */
	private RecordToken communication;
	
	/**
	 * Length of the message being transmitted
	 */
	private int messageLength;
	
	/**
	 * The id of the task that sent this message
	 */
	private int taskId;
	
	/**
	 * The period of the task that sent this message
	 */
	private double taskPeriod;
	
	/**
	 * The time at which the task this message represents was released
	 */
	private double releaseTime;
	
	/**
	 * The time at which the arbitrar was notified that this message needed to transmit
	 * on the bus
	 */
	private double busArrivalTime;
	
	public Message(int sourceProcessor, RecordToken token, double busArrivalTime) {
		this.sourceProcessor = sourceProcessor;
		
		this.token = token;
		this.communication = (RecordToken) token.get("communication");
		this.busArrivalTime  = busArrivalTime;
		
		extractDetailsFromPackets();
	}
	
	public int getSourceProcessor() {
		return sourceProcessor;
	}
	
	public int getDestinationProcessor() {
		return destinationProcessor;
	}
	
	public int getLength() {
		return messageLength;
	}
	
	public boolean isInternal() {
		return getSourceProcessor() == getDestinationProcessor();
	}
	
	public boolean hasBeenTransmitted() {
		return this.hasBeenTransmitted;
	}
	
	public void markAsTransmitted() {
		if (this.hasBeenTransmitted == true) {
			return;
		}
		
		this.hasBeenTransmitted = true;
		
		this.setChanged();
		this.notifyObservers();
		this.clearChanged();
	}
	
	public RecordToken getMessageToken() {
		return token;
	}
	
	private void extractDetailsFromPackets() {
		this.taskId               = ((IntToken) token.get("id")).intValue();
		this.destinationProcessor = ((IntToken) communication.get("destination")).intValue();
		this.messageLength        = ((IntToken) communication.get("messagelength")).intValue();
		this.taskPeriod           = ((DoubleToken) token.get("period")).doubleValue();
		this.releaseTime          = ((DoubleToken) token.get("releasetime")).doubleValue();
	}
	
	
	public boolean hasMissedDeadline(double currentTime) {
		return (currentTime - this.releaseTime) > this.taskPeriod; 
	}

	public int getTaskId() {
		return taskId;
	}

	public double getTaskPeriod() {
		return this.taskPeriod;
	}

	public double getReleaseTime() {
		return this.releaseTime;
	}

	public double getBusArrivalTime() {
		return busArrivalTime;
	}

	public boolean arrivedOnBusBefore(Message peekFirst) {
		return getBusArrivalTime() > peekFirst.getBusArrivalTime();
	}

	public boolean arrivedOnBusAtTheSameTimeAs(Message localBest) {
		return getBusArrivalTime() == localBest.getBusArrivalTime();
	}
}