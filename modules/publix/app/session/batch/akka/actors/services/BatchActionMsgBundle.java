package session.batch.akka.actors.services;

import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg;

/**
 * Just a basic container for up to three BatchActionMsgs
 * 
 * @author Kristian Lange (2017)
 */
public class BatchActionMsgBundle {

	private BatchActionMsg msg1;
	private BatchActionMsg msg2;
	private BatchActionMsg msg3;

	public static BatchActionMsgBundle build(BatchActionMsg msg1,
			BatchActionMsg msg2, BatchActionMsg msg3) {
		BatchActionMsgBundle bundle = new BatchActionMsgBundle();
		bundle.msg1 = msg1;
		bundle.msg2 = msg2;
		bundle.msg3 = msg3;
		return bundle;
	}

	public static BatchActionMsgBundle build(BatchActionMsg msg1,
			BatchActionMsg msg2) {
		BatchActionMsgBundle bundle = new BatchActionMsgBundle();
		bundle.msg1 = msg1;
		bundle.msg2 = msg2;
		return bundle;
	}

	public static BatchActionMsgBundle build(BatchActionMsg msg1) {
		BatchActionMsgBundle bundle = new BatchActionMsgBundle();
		bundle.msg1 = msg1;
		return bundle;
	}

	public BatchActionMsg[] getAll() {
		if (msg1 == null) {
			return new BatchActionMsg[0];
		} else if (msg2 == null) {
			BatchActionMsg[] a = { msg1 };
			return a;
		} else if (msg3 == null) {
			BatchActionMsg[] a = { msg1, msg2 };
			return a;
		} else {
			BatchActionMsg[] a = { msg1, msg2, msg3 };
			return a;
		}
	}
}
