package publix.akka.messages;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class GroupMsg {
	
	public final JsonNode jsonNode;

	public GroupMsg(JsonNode jsonNode) {
		this.jsonNode = jsonNode;
	}
	
	@Override
	public String toString() {
		return jsonNode.asText();
	}
}
