package publix.akka.messages;

import akka.actor.ActorRef;

/**
 * Message exchanged by GroupChannelActors.
 * 
 * @author madsen
 */
public class ItsThisOne {
	
	public final ActorRef channel;

	public ItsThisOne(ActorRef channel) {
		this.channel = channel;
	}
}
