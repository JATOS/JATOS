package publix.groupservices.akka.messages;

/**
 * Like Get but creates the GroupDispatcher if it doesn't exist
 * 
 * @author Kristian Lange (2015)
 */
public class GetOrCreate {

	public final long id;

	public GetOrCreate(long id) {
		this.id = id;
	}

}
