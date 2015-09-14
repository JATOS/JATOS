package publix.groupservices.akka.messages;

import akka.actor.ActorRef;

/**
 * Transports a GroupDispatcher ActorRef as an answer to Get or GetOrCreate
 * 
 * @author Kristian Lange (2015)
 */
public class ItsThisOne {
	
	public final ActorRef groupDispatcher;

	public ItsThisOne(ActorRef groupDispatcher) {
		this.groupDispatcher = groupDispatcher;
	}
}
