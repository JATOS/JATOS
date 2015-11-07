package groupservices.publix.akka.actors;

import groupservices.publix.akka.messages.GroupDispatcherRegistryProtocol.Get;
import groupservices.publix.akka.messages.GroupDispatcherRegistryProtocol.GetOrCreate;
import groupservices.publix.akka.messages.GroupDispatcherRegistryProtocol.ItsThisOne;
import groupservices.publix.akka.messages.GroupDispatcherRegistryProtocol.Unregister;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import play.libs.Akka;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import daos.GroupResultDao;

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
	 * GroupResult's ID to the ActorRef.
	 */
	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();
	private final GroupResultDao groupResultDao;

	@Inject
	public GroupDispatcherRegistry(GroupResultDao groupResultDao) {
		this.groupResultDao = groupResultDao;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			// Someone wants to know the GroupDispatcher for a group result ID
			tellGroupDispatcher((Get) msg);
		} else if (msg instanceof GetOrCreate) {
			// Someone wants to know the GroupDispatcher for a group result ID.
			// If it doesn't exist, create a new one.
			createAndTellGroupDispatcher((GetOrCreate) msg);
		} else if (msg instanceof Unregister) {
			// A GroupDispatcher closed down and wants to unregister
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.groupResultId);
		} else {
			unhandled(msg);
		}
	}

	private void tellGroupDispatcher(Get get) {
		ActorRef groupDispatcher = groupDispatcherMap.get(get.groupResultId);
		ItsThisOne answer = new ItsThisOne(groupDispatcher);
		sender().tell(answer, self());
	}

	private void createAndTellGroupDispatcher(GetOrCreate getOrCreate) {
		ActorRef groupDispatcher = groupDispatcherMap
				.get(getOrCreate.groupResultId);
		if (groupDispatcher == null) {
			groupDispatcher = Akka.system().actorOf(
					GroupDispatcher.props(self(), groupResultDao,
							getOrCreate.groupResultId));
			groupDispatcherMap.put(getOrCreate.groupResultId, groupDispatcher);
		}
		ItsThisOne answer = new ItsThisOne(groupDispatcher);
		sender().tell(answer, self());
	}

}