package y6385133.embs;

import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.util.IllegalActionException;

class Bus {
	/**
	 * Set of processors this bus can communicate with directly
	 */
	private List<Integer> processors;
	
	/**
	 * Messages that are queued for transmission on this bus
	 */
	private LinkedList<Message> backlog = new LinkedList<Message>();
	
	/**
	 * The port that messages should be sent to after "transmitting"
	 * Typically this is actor that does verification analysis
	 */
	private TypedIOPort output;
	
	/**
	 * Port that can be used to receive messages
	 */
	private TypedIOPort input;
	
	/**
	 * The current transmission on the bus
	 * If communicating over the bridge
	 */
	private Transmission currentTransmission;

	/**
	 * The bridge this bus is connected to
	 * TODO: If more than one bridge is needed expand this to multiple
	 * bridges?
	 */
	private Bridge bridge;

	private String id;

	private Parameter bandwidth;
	
	public Bus(String id, Parameter bandwidth, List<Integer> processors, TypedIOPort input, TypedIOPort output) {
		this.id         = id;
		this.bandwidth  = bandwidth;
		this.processors = processors;
		this.input  = input;
		this.output = output;
	}
	
	/**
	 * Set the bridge this bus is connected to
	 * @param bridge
	 */
	public void setBridge(Bridge bridge) {
		this.bridge = bridge;
	}
	
	/**
	 * Get the bridge this bus is connected to
	 * @return
	 */
	public Bridge getBridge() {
		return this.bridge;
	}
	
	/**
	 * Predicate to check that the bus is connected to
	 * @param processor
	 * @return
	 */
	public boolean isConnectedToProcessor(int processor) {
		return processors.contains(processor);
	}
	
	/**
	 * The bandwidth of this bus
	 * @return
	 */
	public double getBandwidth() {
		return Double.parseDouble(bandwidth.getExpression());
	}
	
	/**
	 * Take a job off the queue
	 * @return
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	public double process(double currentTime) throws NoRoomException, IllegalActionException {
		
		if(currentTransmission != null && currentTransmission.hasFinishedTransmitting(currentTime)) {
			System.out.println("["+this.id+"]: <<< Finished transmitting");
			// The transmission is responsible for calling processMessage()
			currentTransmission.endTransmission();
			currentTransmission = null;
		}
		
		for (int i=0,width=input.getWidth();i<width;++i) {
			if (input.hasToken(i)) {
				RecordToken packet = (RecordToken) input.get(i);
				
				enqueue(new Message(processors.get(i), packet));
			}
		}
		
		// If we're able to dequeue a message in our buffer
		if (dequeue(currentTime)) {
			System.out.println("["+this.id+"]: >>> Transmitting a message that will finish at "+currentTransmission.getFinishTime());
			return currentTransmission.getFinishTime();
		}
		
		return 0;
	}
	
	/**
	 * Queues a message for transmission on the bus
	 * 
	 * If the communication is internal to the processor that is sending the message,
	 * it is processed immediately by that processor
	 *  
	 * @param message
	 * @throws NoRoomException
	 * @throws IllegalActionException
	 */
	public void enqueue(Message message) throws NoRoomException, IllegalActionException {
		System.out.println("["+this.id+"]: --- Enqueueing message from:"+message.getTaskId()+"("+message.getSourceProcessor()+") internal: "+message.isInternal());
		
		if (message.isInternal()) {
			processMessage(message);
		} else {
			backlog.push(message);
		}
	}
	
	/**
	 * 
	 * @param communication
	 * @throws NoRoomException
	 * @throws IllegalActionException
	 */
	public void processMessage(Message communication) throws NoRoomException, IllegalActionException {
		System.out.println("["+this.id+"]: Sending message to "+communication.getDestinationProcessor());
		output.send(communication.getSourceProcessor(), communication.getMessageToken());
	}
	
	/**
	 * Attempt to start transmitting a message that is currently in the buffer 
	 * @param currentTime
	 * @return
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	private boolean dequeue(double currentTime) throws NoRoomException, IllegalActionException {
		if ( ! backlog.isEmpty() && currentTransmission == null) {
			Message newMessage = backlog.removeFirst();
			
			if(this.isConnectedToProcessor(newMessage.getDestinationProcessor())) {
				currentTransmission = new BusTransmission(currentTime, newMessage, this);
			} else {
				currentTransmission = new BridgeTransmission(newMessage, this);
			}
		
			return true;
		}
		
		return false;
	}
}