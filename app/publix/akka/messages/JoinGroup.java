package publix.akka.messages;

import akka.actor.ActorRef;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class JoinGroup {

	public final long studyResultId;
	public final ActorRef systemChannel;

	public JoinGroup(long studyResultId, ActorRef systemChannel) {
		this.studyResultId = studyResultId;
		this.systemChannel = systemChannel;
	}

}
