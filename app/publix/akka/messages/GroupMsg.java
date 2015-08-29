package publix.akka.messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class GroupMsg {
	
	public final ObjectNode jsonNode;

	public GroupMsg(ObjectNode jsonNode) {
		this.jsonNode = jsonNode;
	}
	
	@Override
	public String toString() {
		return jsonNode.asText();
	}
}
