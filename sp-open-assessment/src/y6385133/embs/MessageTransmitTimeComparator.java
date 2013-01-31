package y6385133.embs;

import java.util.Comparator;

public class MessageTransmitTimeComparator implements Comparator<Message>{

	@Override
	public int compare(Message o1, Message o2) {
		return Double.compare(o1.getBusArrivalTime(), o1.getBusArrivalTime());
	}

}
