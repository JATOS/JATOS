package publix.groupservices.akka.messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contains all messages that can be used by the GroupDispatcher Akka Actor.
 * Each message is a static class.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupDispatcherProtocol {

	/**
	 * Message an GroupChannel can send to a GroupDispatcher to indicate it's
	 * closure.
	 */
	public static class ChannelClosed {

		public long studyResultId;

		public ChannelClosed(long studyResultId) {
			this.studyResultId = studyResultId;
		}

	}

	/**
	 * Message send from one GroupChannel via GroupDispatcher to other
	 * GroupChannels. A GroupMsg contains a JSON node.
	 */
	public static class GroupMsg {

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

	/**
	 * Message a GroupChannel can send to join a GroupDispatcher.
	 */
	public static class Join {

		public final long studyResultId;

		public Join(long studyResultId) {
			this.studyResultId = studyResultId;
		}

	}

	/**
	 * Message that forces a GroupChannel to close itself. Send to a
	 * GroupDispatcher it will be forwarded to the right GroupChannel.
	 */
	public static class PoisonChannel {

		public long studyResultIdOfTheOneToPoison;

		public PoisonChannel(long studyResultIdOfTheOneToPoison) {
			this.studyResultIdOfTheOneToPoison = studyResultIdOfTheOneToPoison;
		}

	}

}
