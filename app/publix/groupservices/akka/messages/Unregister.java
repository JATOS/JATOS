package publix.groupservices.akka.messages;

/**
 * @author Kristian Lange
 */
public class Unregister {
	
	public long groupResultId;
	
	public Unregister(long groupResultId) {
		this.groupResultId = groupResultId;
	}

}
