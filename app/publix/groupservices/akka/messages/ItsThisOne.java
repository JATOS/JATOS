package publix.groupservices.akka.messages;

import akka.actor.ActorRef;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class ItsThisOne {
	
	public final ActorRef groupDispatcher;

	public ItsThisOne(ActorRef groupDispatcher) {
		this.groupDispatcher = groupDispatcher;
	}
}
