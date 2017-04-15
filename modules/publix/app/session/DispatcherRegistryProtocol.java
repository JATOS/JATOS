package session;

import akka.actor.ActorRef;

/**
 * Contains all messages that can be used by the DispatcherRegistry Akka Actor.
 * Each message is a static class.
 * 
 * @author Kristian Lange (2017)
 */
public class DispatcherRegistryProtocol {

	/**
	 * Getter for an dispatcher
	 */
	public static class Get {

		public final long id;

		public Get(long id) {
			this.id = id;
		}
	}

	/**
	 * Like Get but creates the dispatcher if it doesn't exist
	 */
	public static class GetOrCreate {

		public final long id;

		public GetOrCreate(long id) {
			this.id = id;
		}
	}

	/**
	 * Transports a dispatcher ActorRef as an answer to Get or GetOrCreate
	 */
	public static class ItsThisOne {

		public final ActorRef dispatcher;

		public ItsThisOne(ActorRef dispatcher) {
			this.dispatcher = dispatcher;
		}
	}

	/**
	 * Unregister a dispatcher from a DispatcherRegistry
	 */
	public static class Unregister {

		public long id;

		public Unregister(long id) {
			this.id = id;
		}
	}

}
