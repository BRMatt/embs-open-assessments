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

	private HierachialBusActor actor;
	
	public Bus(String id, Parameter bandwidth, List<Integer> processors, TypedIOPort input, TypedIOPort output, HierachialBusActor actor) {
		this.id         = id;
		this.bandwidth  = bandwidth;
		this.processors = processors;
		this.input  = input;
		this.output = output;
		this.actor = actor;
	}
	
	public HierachialBusActor getActor() {
		return this.actor;
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
	 * The transmission currently going over the bus
	 * @return
	 */
	public Transmission getCurrentTransmission() {
		return this.currentTransmission;
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
			log("Finished transmitting "+logRoutingSnippet(currentTransmission.getMessage())+" R>T:"+currentTransmission.getMessage().hasMissedDeadline(currentTime));
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
			log("Transmitting "+logRoutingSnippet(currentTransmission.getMessage())+" f:="+currentTransmission.getFinishTime()+" r:="+currentTransmission.getCurrentResponseTime(currentTime)+" t:="+currentTransmission.getMessage().getTaskPeriod());
			return currentTransmission.getFinishTime();
		}
		
		return 0;
	}
	
	private String logRoutingSnippet(Message message) {
		return message.getTaskId()+"("+message.getSourceProcessor()+"->"+message.getDestinationProcessor()+")";
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
		log("--- Recieved message from:"+message.getSourceProcessor()+"("+message.getTaskId()+") -> "+message.getDestinationProcessor()+" internal: "+message.isInternal()+" connected: "+this.isConnectedToProcessor(message.getDestinationProcessor()));
		
		if (message.isInternal()) {
			log("--- Message is internal to processor, skipping queue");
			processMessage(message);
		} else {
			backlog.push(message);
			log("--- Pushed onto backlog");
		}
	}
	
	/**
	 * 
	 * @param communication
	 * @throws NoRoomException
	 * @throws IllegalActionException
	 */
	public void processMessage(Message communication) throws NoRoomException, IllegalActionException {
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
	
	private void log(String message) {
		String processing = this.currentTransmission == null ? "." : "!";
		System.out.println(this.actor.getDirector().getCurrentTime()+" ["+this.id+processing+"("+this.backlog.size()+")]: "+message);
	}
}