package publix.akka.messages;

/**
 * @author Kristian Lange
 */
public class Unregister {
	
	public long id;
	
	public Unregister(long id) {
		this.id = id;
	}

}
