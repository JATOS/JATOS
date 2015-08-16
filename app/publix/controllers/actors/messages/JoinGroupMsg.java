package publix.controllers.actors.messages;

import akka.actor.ActorRef;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class JoinGroupMsg {

	public final long studyResultId;
	public final ActorRef systemChannel;

	public JoinGroupMsg(long studyResultId, ActorRef systemChannel) {
		this.studyResultId = studyResultId;
		this.systemChannel = systemChannel;
	}

}
