package session.batch.akka.actors;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contains all messages that can be used by the BatchDispatcher Akka Actor.
 * Each message is a static class.
 * 
 * @author Kristian Lange (2015)
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
	 * If the JSON node has a key named 'recipient' the message is intended for
	 * only one batch member - otherwise it's a broadcast message.
	 * 
	 * For system messages the special GroupActionMsg is used. For sending an
	 * error message the special GroupErrorMsg is used.
	 * 
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
	 * Special BatchMsg that contains a BatchAction. A batch action message is
	 * like a system event and used solely for messages between the
	 * BatchDispatcher and its batch members. A BatchActionMsg is specified by
	 * an key named 'action' in the JSON node.
	 */
	public static class BatchActionMsg extends BatchMsg {

		/**
		 * All possible batch actions a batch action message can have.
		 */
		public enum BatchAction {
			OPENED, // Signals TODO
			CLOSED, // Signals TODO
			SESSION, // Signals this message contains a batch session update
			SESSION_ACK, // Signals that the session update was successful
			SESSION_FAIL, // Signals that the session update failed
			ERROR // Used to send an error back to the sender
		};

		public enum TellWhom {
			ALL, ALL_BUT_SENDER, SENDER_ONLY
		};

		public TellWhom tellWhom;

		public BatchActionMsg(ObjectNode jsonNode, TellWhom tellWhom) {
			super(jsonNode);
			this.tellWhom = tellWhom;
		}

		/**
		 * JSON variables that can be send in a GroupActionMsg
		 */
		public static final String ACTION = "action";
		public static final String BATCH_SESSION_DATA = "data";
		public static final String BATCH_SESSION_PATCHES = "patches";
		public static final String BATCH_SESSION_VERSION = "version";
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
