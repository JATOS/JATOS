package publix.akka.messages;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class GroupMsg {
	
	public final String msg;

	public GroupMsg(Object msg) {
		this.msg = msg.toString();
	}
	
	@Override
	public String toString() {
		return msg;
	}
}
