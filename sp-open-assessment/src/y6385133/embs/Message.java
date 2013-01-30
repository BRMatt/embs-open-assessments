package y6385133.embs;

import java.util.Observable;

import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;

class Message extends Observable {
	private int sourceProcessor;
	private RecordToken token;
	private int destinationProcessor;
	private boolean hasBeenTransmitted = false;
	private RecordToken communication;
	private int messageLength;
	private int taskId;
	private double taskPeriod;
	private double releaseTime;
	
	public Message(int sourceProcessor, RecordToken token) {
		this.sourceProcessor = sourceProcessor;
		
		this.token = token;
		this.communication = (RecordToken) token.get("communication");
		
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
}