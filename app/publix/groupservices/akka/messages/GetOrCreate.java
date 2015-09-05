package publix.groupservices.akka.messages;

/**
 * @author Kristian Lange
 */
public class GetOrCreate {

	public final long id;

	public GetOrCreate(long id) {
		this.id = id;
	}

}
