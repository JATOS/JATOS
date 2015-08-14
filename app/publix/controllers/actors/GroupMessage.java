package publix.controllers.actors;

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
