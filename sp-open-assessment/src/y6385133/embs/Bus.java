package y6385133.embs;

import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

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

	/**
	 * This is the next candidate for transmission on the bus
	 * This represents the message that we know for definite can be transmitted
	 * on the bus
	 */
	private Message nextForTransmission;
	
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
	 * Maintenance includes things like checking that the current transmission has finished processing
	 * and reading messages from input ports
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	public void performMaintenance(double currentTime) throws NoRoomException, IllegalActionException {
		if(currentTransmission != null && currentTransmission.hasFinishedTransmitting(currentTime)) {
			log("Finished transmitting "+logRoutingSnippet(currentTransmission.getMessage())+" R>T:"+currentTransmission.getMessage().hasMissedDeadline(currentTime));
			// The transmission is responsible for calling processMessage()
			currentTransmission.endTransmission();
			currentTransmission = null;
		}
		
		for (int i=0,width=input.getWidth();i<width;++i) {
			if (input.hasToken(i)) {
				RecordToken packet = (RecordToken) input.get(i);
				
				enqueue(new Message(processors.get(i), packet, currentTime));
			}
		}
	}
	
	/**
	 * Attempts to find the next best message to transmit on this bus
	 * 
	 * @param currentTime
	 */
	public void performArbitration(double currentTime) {
		if(nextForTransmission == null && ! backlog.isEmpty()) {
			Message transmissionCandidate = backlog.peekFirst();
			
			if(destinationIsOnThisBus(transmissionCandidate) || remoteBusWillArbitrateMessage(transmissionCandidate)) {
				nextForTransmission = transmissionCandidate;
			}
		}
	}
	
	/**
	 * Take a job off the queue
	 * @return
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	public double performTransmission(double currentTime) throws NoRoomException, IllegalActionException {
		// If we're able to dequeue a message in our buffer
		if (dequeue(currentTime)) {
			log("Transmitting "+logRoutingSnippet(currentTransmission.getMessage())+" f:="+currentTransmission.getFinishTime()+" r:="+currentTransmission.getCurrentResponseTime(currentTime)+" t:="+currentTransmission.getMessage().getTaskPeriod());
			
			return currentTransmission.getFinishTime();
		}
		
		return 0;
	}
	
	
	
	/**
	 * Allows the bridge to request this bus start transmitting a message locally
	 * The arbitrar will only allow the incoming message to start transmitting if:
	 * 
	 * * There is nothing currently running
	 * * This bus is connected to the processor the message requires
	 * * The best candidate for arbitration on this bus arrived after the message
	 * 
	 * @param message
	 * @return
	 */
	public boolean requestTransmissionToken(Message message) {
		if (currentTransmission != null) {
			log("=== Transmission token for "+logRoutingSnippet(message)+" denied - currently running");
			return false;
		}
		
		if( ! isConnectedToProcessor(message.getDestinationProcessor())) {
			log("=== Transmission token for "+logRoutingSnippet(message)+" denied - not connected to processor");
			return false;
		}
		
		if(isConnectedToProcessor(message.getSourceProcessor())) {
			log("### Something has gone wrong, not granting remote transmission token to myself! "+logRoutingSnippet(message));
			return false;
		}
		
		Message localBest = null;
		
		if ( ! backlog.isEmpty()) {
			localBest = backlog.peek();
			
			if (localBest.arrivedOnBusBefore(message)) {
				log("=== Transmission token for "+logRoutingSnippet(message)+" denied - local master requested transmission earlier");
				return false;
			}

			// TODO: Add some least-laxity shizzle?
		}

		log("=== Transmission token granted for remote message "+logRoutingSnippet(message)+(localBest == null ? "" : "local:"+localBest.getBusArrivalTime()+" remote:"+message.getBusArrivalTime()));
		
		nextForTransmission = message;
		backlog.addFirst(message);
		
		return true;
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
	private void enqueue(Message message) throws NoRoomException, IllegalActionException {
		log("--- Recieved message from:"+logRoutingSnippet(message)+" internal: "+message.isInternal()+" connected: "+this.isConnectedToProcessor(message.getDestinationProcessor()));
		
		if (message.isInternal()) {
			log("--- Message is internal to processor, skipping queue");
			processMessage(message);
		} else {
			backlog.add(message);
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
	 * Will only start transmitting a message if 
	 * 
	 * a) There is something to transmit
	 * b) Something is not currently transmitting
	 * c) Arbitration is possible
	 *
	 * @param currentTime
	 * @return
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	private boolean dequeue(double currentTime) throws NoRoomException, IllegalActionException {
		if ( nextForTransmission == null || currentTransmission != null ) {
			return false;
		}
		
		if(isConnectedToProcessor(nextForTransmission.getDestinationProcessor())) {
			log("+++ Setting up internal transmission for "+logRoutingSnippet(nextForTransmission));
			currentTransmission = new BusTransmission(currentTime, backlog.removeFirst(), this);
		} else {
			log("+++ Setting up cross-bridge transmission for "+logRoutingSnippet(nextForTransmission));
			// nextForTransmission is only populated with a message that crosses the bridge
			// if we have already established with the remote bridge that it will be arbitrated
			currentTransmission = new BridgeTransmission(backlog.removeFirst(), this);
		}
		
		nextForTransmission = null;
		
		return true;
	}
	
	private boolean destinationIsOnThisBus(Message transmissionCandidate){
		return isConnectedToProcessor(transmissionCandidate.getDestinationProcessor());
	}
	
	private boolean remoteBusWillArbitrateMessage(Message transmissionCandidate) {
		return getBridge().reserveRemoteArbitration(transmissionCandidate);
	}
	
	private void log(String message) {
		String processing = this.currentTransmission == null ? "." : "!";
		System.out.println(this.actor.getDirector().getCurrentTime()+" ["+this.id+processing+"("+this.backlog.size()+")]: "+message);
	}
	
	private String logRoutingSnippet(Message message) {
		return message.getTaskId()+"("+message.getSourceProcessor()+"->"+message.getDestinationProcessor()+")";
	}	
}