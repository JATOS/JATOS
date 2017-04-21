package session.batch.akka.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contains all messages that can be used by the BatchDispatcher Akka Actor.
 * Each message is a static class.
 * 
 * @author Kristian Lange (2017)
 */
public class BatchDispatcherProtocol {

	/**
	 * Message an BatchChannel can send to its BatchDispatcher to indicate it's
	 * closure.
	 */
	public static class UnregisterChannel {

		public final long studyResultId;

		public UnregisterChannel(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message format used for communication in the batch channel between the
	 * BatchDispatcher and the batch members. A BatchMsg contains a JSON node.
	 */
	public static class BatchMsg {

		public final ObjectNode jsonNode;

		public BatchMsg(ObjectNode jsonNode) {
			this.jsonNode = jsonNode;
		}

		@Override
		public String toString() {
			return jsonNode.asText();
		}
	}

	/**
	 * Special BatchMsg that contains a batch action. A BatchActionMsg is
	 * defined by an key named 'action' in the JSON node. A batch action message
	 * is like a system event and used solely for messages between the
	 * BatchDispatcher and its batch members. Optionally it can define an field
	 * of type TellWhom, which is used by the dispatcher to determine the
	 * recipients.
	 */
	public static class BatchActionMsg extends BatchMsg {

		public enum TellWhom {
			ALL, ALL_BUT_SENDER, SENDER_ONLY
		};

		public TellWhom tellWhom;

		public BatchActionMsg(ObjectNode jsonNode, TellWhom tellWhom) {
			super(jsonNode);
			this.tellWhom = tellWhom;
		}

		/**
		 * All possible batch actions a batch action message can have. They are
		 * used as values in JSON message's action field.
		 */
		public enum BatchAction {
			OPENED, // Signals the opening of a batch channel
			CLOSED, // Signals the closing of a batch channel
			SESSION, // Signals this message contains a batch session update
			SESSION_ACK, // Signals that the session update was successful
			SESSION_FAIL, // Signals that the session update failed
			ERROR // Used to send an error back to the sender
		};

		/**
		 * JSON key name for an action (mandatory for an BatchActionMsg)
		 */
		public static final String ACTION = "action";
		/**
		 * JSON key name for session data (must be accompanied with a session
		 * version)
		 */
		public static final String BATCH_SESSION_DATA = "data";
		/**
		 * JSON key name for a session patches (must be accompanied with a
		 * session version)
		 */
		public static final String BATCH_SESSION_PATCHES = "patches";
		/**
		 * JSON key name for the batch session version (always together with
		 * either session data or patches)
		 */
		public static final String BATCH_SESSION_VERSION = "version";
		/**
		 * JSON key name for an error message
		 */
		public static final String ERROR_MSG = "errorMsg";

	}

	/**
	 * Message a BatchChannel can send to register in a BatchDispatcher.
	 */
	public static class RegisterChannel {

		public final long studyResultId;

		public RegisterChannel(long studyResultId) {
			this.studyResultId = studyResultId;
		}
	}

	/**
	 * Message that forces a BatchChannel to close itself. Send to a
	 * BatchDispatcher it will be forwarded to the right BatchChannel.
	 */
	public static class PoisonChannel {

		public final long studyResultIdOfTheOneToPoison;

		public PoisonChannel(long studyResultIdOfTheOneToPoison) {
			this.studyResultIdOfTheOneToPoison = studyResultIdOfTheOneToPoison;
		}
	}

}
