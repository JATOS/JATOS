package publix.akka.messages;

/**
 * @author madsen
 */
public class SystemMsg {
	
	public final String msg;

	public SystemMsg(Object msg) {
		this.msg = msg.toString();
	}
	
	@Override
	public String toString() {
		return msg;
	}
}
