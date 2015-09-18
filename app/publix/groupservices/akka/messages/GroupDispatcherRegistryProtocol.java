package publix.groupservices.akka.messages;

import akka.actor.ActorRef;

/**
 * Contains all messages that can be used by the GroupDispatcherRegistry Akka
 * Actor. Each message is a static class.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupDispatcherRegistryProtocol {

	/**
	 * Getter for a GroupDispatcher actor
	 */
	public static class Get {

		public final long groupId;

		public Get(long groupId) {
			this.groupId = groupId;
		}
	}

	/**
	 * Like Get but creates the GroupDispatcher if it doesn't exist
	 */
	public static class GetOrCreate {

		public final long groupId;

		public GetOrCreate(long groupId) {
			this.groupId = groupId;
		}
	}

	/**
	 * Transports a GroupDispatcher ActorRef as an answer to Get or GetOrCreate
	 */
	public static class ItsThisOne {

		public final ActorRef groupDispatcher;

		public ItsThisOne(ActorRef groupDispatcher) {
			this.groupDispatcher = groupDispatcher;
		}
	}

	/**
	 * Unregister a GroupDispatcher from a GroupDispatcherRegistry
	 */
	public static class Unregister {

		public long groupId;

		public Unregister(long groupId) {
			this.groupId = groupId;
		}
	}

}
