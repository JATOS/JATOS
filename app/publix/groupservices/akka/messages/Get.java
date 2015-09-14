package publix.groupservices.akka.messages;

/**
 * Getter for a GroupDispatcher actor
 * 
 * @author Kristian Lange (2015)
 */
public class Get {

	public final long id;

	public Get(long id) {
		this.id = id;
	}

}
