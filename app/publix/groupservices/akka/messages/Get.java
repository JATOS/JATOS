package publix.groupservices.akka.messages;

/**
 * Getter for a GroupDispatcher actor
 * 
 * @author Kristian Lange (2015)
 */
public class Get {

	public final long groupId;

	public Get(long groupId) {
		this.groupId = groupId;
	}

}
