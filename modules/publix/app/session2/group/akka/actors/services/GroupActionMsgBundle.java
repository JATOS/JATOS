package session2.group.akka.actors.services;

import session2.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg;

/**
 * Just a basic container for up to three GroupActionMsgs
 * 
 * @author Kristian Lange (2017)
 */
public class GroupActionMsgBundle {
	
	private GroupActionMsg msg1;
	private GroupActionMsg msg2;
	private GroupActionMsg msg3;

	public static GroupActionMsgBundle build(GroupActionMsg msg1,
			GroupActionMsg msg2, GroupActionMsg msg3) {
		GroupActionMsgBundle bundle = new GroupActionMsgBundle();
		bundle.msg1 = msg1;
		bundle.msg2 = msg2;
		bundle.msg3 = msg3;
		return bundle;
	}

	public static GroupActionMsgBundle build(GroupActionMsg msg1,
			GroupActionMsg msg2) {
		GroupActionMsgBundle bundle = new GroupActionMsgBundle();
		bundle.msg1 = msg1;
		bundle.msg2 = msg2;
		return bundle;
	}

	public static GroupActionMsgBundle build(GroupActionMsg msg1) {
		GroupActionMsgBundle bundle = new GroupActionMsgBundle();
		bundle.msg1 = msg1;
		return bundle;
	}

	public GroupActionMsg[] getAll() {
		if (msg1 == null) {
			return new GroupActionMsg[0];
		} else if (msg2 == null) {
			GroupActionMsg[] a = { msg1 };
			return a;
		} else if (msg3 == null) {
			GroupActionMsg[] a = { msg1, msg2 };
			return a;
		} else {
			GroupActionMsg[] a = { msg1, msg2, msg3 };
			return a;
		}
	}
}
