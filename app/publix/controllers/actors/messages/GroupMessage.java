package publix.controllers.actors.messages;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class GroupMessage {
	
	public final String msg;

	public GroupMessage(Object msg) {
		this.msg = msg.toString();
	}
	
	@Override
	public String toString() {
		return msg;
	}
}
