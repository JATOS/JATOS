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
	 * Message to a GroupDispatcher. The GroupDispatcher will tell all other
	 * members of its group about the new member. This will NOT open a new group
	 * channel (a group channel is opened by the WebSocketBuilder and registers
	 * only with a GroupDispatcher).
	 */
	public static class Joined {

		public long studyResultId;

		public Joined(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message to a GroupDispatcher. The GroupDispatcher will just tell all
	 * other members of its group about the left member. This will NOT close the
	 * group channel (a group channel is closed by sending a PoisonChannel
	 * message.
	 */
	public static class Left {

		public long studyResultId;

		public Left(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message an GroupChannel can send to its GroupDispatcher to indicate it's
	 * closure.
	 */
	public static class UnregisterChannel {

		public long studyResultId;

		public UnregisterChannel(long studyResultId) {
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
		public static final String OPENED = "opened";
		public static final String CLOSED = "closed";
		public static final String GROUP_ID = "groupId";
		public static final String MEMBERS = "members";
		public static final String CHANNELS = "channels";
		public static final String STATE = "state";
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

		public long studyResultIdOfTheOneToPoison;

		public PoisonChannel(long studyResultIdOfTheOneToPoison) {
			this.studyResultIdOfTheOneToPoison = studyResultIdOfTheOneToPoison;
		}
	}

}
