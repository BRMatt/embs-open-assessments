package y6385133.embs;

import ptolemy.data.RecordToken;

class MessageCommunication implements Communication {
	private int sourceChannel;
	private RecordToken communication;
	private int destinationChannel;
	public MessageCommunication(int sourceChannel, RecordToken communication) {
		this.sourceChannel = sourceChannel;
		this.communication = communication;
	}
	
	public int getSourceChannel() {
		return sourceChannel;
	}
	
	public int getDestinationChannel() {
		return destinationChannel;
	}
	
	public boolean isInternal() {
		return getSourceChannel() == getDestinationChannel();
	}
	
	public RecordToken getMessage() {
		return communication;
	}
}