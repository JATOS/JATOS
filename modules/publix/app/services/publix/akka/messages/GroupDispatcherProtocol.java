package services.publix.akka.messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contains all messages that can be used by the GroupDispatcher Akka Actor.
 * Each message is a static class.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupDispatcherProtocol {

	/**
	 * Message to a GroupDispatcher. The GroupDispatcher will tell all other
	 * members of its group about the new member. This will NOT open a new group
	 * channel (a group channel is opened by the WebSocketBuilder and registers
	 * only with a GroupDispatcher).
	 */
	public static class Joined {

		public final long studyResultId;

		public Joined(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message to a GroupDispatcher. The GroupDispatcher will just tell all
	 * other members of its GroupResult about the left member. This will NOT
	 * close the group channel (a group channel is closed by sending a
	 * PoisonChannel message.
	 */
	public static class Left {

		public final long studyResultId;

		public Left(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message an GroupChannel can send to its GroupDispatcher to indicate it's
	 * closure.
	 */
	public static class UnregisterChannel {

		public final long studyResultId;

		public UnregisterChannel(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message format used for communication in the group channel between the
	 * GroupDispatcher and the group members. A GroupMsg contains a JSON node.
	 * If the JSON node has a key named 'recipient' the message is intended for
	 * only one group member - otherwise it's a broadcast message.
	 * 
	 * For system messages the special GroupActionMsg is used. For sending an
	 * error message the special GroupErrorMsg is used.
	 * 
	 */
	public static class GroupMsg {

		public static final String RECIPIENT = "recipient";

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
	 * Special GroupMsg that contains a GroupAction. A group action message is
	 * like a system event and used solely for messages between the
	 * GroupDispatcher and its group members. A GroupActionMsg is specified by
	 * an key named 'action' in the JSON node.
	 */
	public static class GroupActionMsg extends GroupMsg {

		/**
		 * All possible group actions a group action message can have.
		 */
		public enum GroupAction {
			JOINED, LEFT, OPENED, CLOSED, GROUP_SESSION
		};

		public GroupActionMsg(ObjectNode jsonNode) {
			super(jsonNode);
		}

		/**
		 * JSON variables that can be send in a GroupActionMsg
		 */
		public static final String ACTION = "action";
		public static final String GROUP_RESULT_ID = "groupResultId";
		public static final String MEMBER_ID = "memberId";
		public static final String MEMBERS = "members";
		public static final String CHANNELS = "channels";
		public static final String GROUP_SESSION_DATA = "groupSessionData";
		public static final String GROUP_SESSION_VERSION = "groupSessionVersion";

	}

	/**
	 * A special GroupMsg that contains an error message send from the
	 * GroupDispatcher to a group member. A GroupActionMsg is specified by an
	 * key named 'error' in the JSON node.
	 */
	public static class GroupErrorMsg extends GroupMsg {

		public static final String ERROR = "error";

		public GroupErrorMsg(ObjectNode jsonNode) {
			super(jsonNode);
		}

	}

	/**
	 * Message a GroupChannel can send to register in a GroupDispatcher.
	 */
	public static class RegisterChannel {

		public final long studyResultId;

		public RegisterChannel(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message that forces a GroupChannel to close itself. Send to a
	 * GroupDispatcher it will be forwarded to the right GroupChannel.
	 */
	public static class PoisonChannel {

		public final long studyResultIdOfTheOneToPoison;

		public PoisonChannel(long studyResultIdOfTheOneToPoison) {
			this.studyResultIdOfTheOneToPoison = studyResultIdOfTheOneToPoison;
		}
	}

}
