package publix.groupservices.akka.actors;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import persistance.GroupDao;
import play.libs.Akka;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.Get;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.GetOrCreate;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.ItsThisOne;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.Unregister;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

/**
 * A GroupDispatcherRegistry is an Akka Actor keeps track of all
 * GroupDispatchers Actors.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class GroupDispatcherRegistry extends UntypedActor {

	public static final String ACTOR_NAME = "GroupDispatcherRegistry";

	/**
	 * Contains the GroupDispatchers that are currently registered. Maps the
	 * GroupModel's ID to the ActorRef.
	 */
	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();
	private final GroupDao groupDao;

	@Inject
	public GroupDispatcherRegistry(GroupDao groupDao) {
		this.groupDao = groupDao;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			// Someone wants to know the GroupDispatcher for a group ID
			tellGroupDispatcher((Get) msg);
		} else if (msg instanceof GetOrCreate) {
			// Someone wants to know the GroupDispatcher for a group ID. If it
			// doesn't exist, create a new one.
			createAndTellGroupDispatcher((GetOrCreate) msg);
		} else if (msg instanceof Unregister) {
			// A GroupDispatcher closed down and wants to unregister
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.groupId);
		} else {
			unhandled(msg);
		}
	}

	private void tellGroupDispatcher(Get get) {
		ActorRef groupDispatcher = groupDispatcherMap.get(get.groupId);
		ItsThisOne answer = new ItsThisOne(groupDispatcher);
		sender().tell(answer, self());
	}

	private void createAndTellGroupDispatcher(GetOrCreate getOrCreate) {
		ActorRef groupDispatcher = groupDispatcherMap.get(getOrCreate.groupId);
		if (groupDispatcher == null) {
			groupDispatcher = Akka.system().actorOf(
					GroupDispatcher
							.props(self(), groupDao, getOrCreate.groupId));
			groupDispatcherMap.put(getOrCreate.groupId, groupDispatcher);
		}
		ItsThisOne answer = new ItsThisOne(groupDispatcher);
		sender().tell(answer, self());
	}

}