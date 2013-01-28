package y6385133.embs;

import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;

class Message {
	private int sourceChannel;
	private RecordToken token;
	private int destinationProcessor;
	
	public Message(int sourceChannel, RecordToken token) {
		this.sourceChannel = sourceChannel;
		this.destinationProcessor = ((IntToken) token.get("destination")).intValue();
		this.token = token;
	}
	
	public int getSourceChannel() {
		return sourceChannel;
	}
	
	public int getDestinationProcessor() {
		return destinationProcessor;
	}
	
	public int getLength() {
		return ((IntToken) token.get("messagelength")).intValue();
	}
	
	public boolean isInternal() {
		return getSourceChannel() == getDestinationProcessor();
	}
	
	public RecordToken getMessageToken() {
		return token;
	}
}