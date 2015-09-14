package publix.groupservices.akka.messages;

/**
 * Unregister a GroupDispatcher from a GroupDispatcherRegistry
 * 
 * @author Kristian Lange (2015)
 */
public class Unregister {
	
	public long groupId;
	
	public Unregister(long groupId) {
		this.groupId = groupId;
	}

}
