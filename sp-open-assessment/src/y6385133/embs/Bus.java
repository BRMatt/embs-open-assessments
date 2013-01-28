package y6385133.embs;

import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedIOPort;
import ptolemy.kernel.util.IllegalActionException;

class Bus {
	private LinkedList<MessageCommunication> backlog = new LinkedList<MessageCommunication>();
	private TypedIOPort output;
	private double doneTime;
	private boolean busy;
	private TypedIOPort input;
	private List<Integer> processors;
	
	public Bus(List<Integer> processors, TypedIOPort input, TypedIOPort output) {
		this.processors = processors;
		this.input  = input;
		this.output = output;
	}

	/**
	 * Queues a communication for transmission on the bus
	 * If the communication is internal to the processor it is processed immediately
	 *  
	 * @param communication
	 * @throws NoRoomException
	 * @throws IllegalActionException
	 */
	public void enqueue(MessageCommunication communication) throws NoRoomException, IllegalActionException {
		if (communication.isInternal()) {
			processJob(communication);
		} else {
			backlog.push(communication);
		}
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
	 * Check to see if the bus is communicating at this moment in time
	 * 
	 * @param currentTime The current time in the simulation
	 * @return
	 */
	public boolean isTransmitting(double currentTime) {
		return currentTime < doneTime;
	}
	
	
	/**
	 * Take a job off the queue
	 * @return
	 * @throws IllegalActionException 
	 * @throws NoRoomException 
	 */
	public double process(double currentTime) throws NoRoomException, IllegalActionException {
		if(busy && ! isTransmitting(currentTime)) {
			processJob(backlog.pop());
			busy = false;
		}
		
		return 0;			
	}
	
	private void processJob(MessageCommunication communication) throws NoRoomException, IllegalActionException {
		output.send(communication.getSourceChannel(), communication.getMessage());
	}
}