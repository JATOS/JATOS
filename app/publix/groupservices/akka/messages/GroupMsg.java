package publix.groupservices.akka.messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Message send from one GroupChannel via GroupDispatcher to other GroupChannels
 * 
 * @author Kristian Lange (2015)
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
