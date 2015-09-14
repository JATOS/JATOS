package publix.groupservices.akka.messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Message send from one GroupChannel via GroupDispatcher to other GroupChannels
 * 
 * @author Kristian Lange (2015)
 */
public class GroupMsg {

	/**
	 * JSON variables that can be send in a GroupMsg
	 */
	public static final String JOINED = "joined";
	public static final String LEFT = "left";
	public static final String GROUP_ID = "groupId";
	public static final String GROUP_MEMBERS = "groupMembers";
	public static final String GROUP_STATE = "groupState";
	public static final String RECIPIENT = "recipient";
	public static final String ERROR = "error";

	public final ObjectNode jsonNode;

	public GroupMsg(ObjectNode jsonNode) {
		this.jsonNode = jsonNode;
	}

	@Override
	public String toString() {
		return jsonNode.asText();
	}
}
