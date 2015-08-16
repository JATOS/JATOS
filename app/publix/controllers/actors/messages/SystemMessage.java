package publix.controllers.actors.messages;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class SystemMessage {
	
	public final String msg;

	public SystemMessage(Object msg) {
		this.msg = msg.toString();
	}
	
	@Override
	public String toString() {
		return msg;
	}
}
