package publix.groupservices.akka.messages;

/**
 * @author Kristian Lange
 */
public class Unregister {
	
	public long groupId;
	
	public Unregister(long groupId) {
		this.groupId = groupId;
	}

}
