package publix.groupservices.akka.actors;

import java.util.HashMap;
import java.util.Map;

import play.libs.Akka;
import publix.groupservices.GroupService;
import publix.groupservices.akka.messages.Get;
import publix.groupservices.akka.messages.GetOrCreate;
import publix.groupservices.akka.messages.ItsThisOne;
import publix.groupservices.akka.messages.PoisonSomeone;
import publix.groupservices.akka.messages.Unregister;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * A GroupDispatcherRegistry is an Akka Actor keeps track of all
 * GroupDispatcher.
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
	private final GroupService groupService;

	@Inject
	public GroupDispatcherRegistry(GroupService groupService) {
		this.groupService = groupService;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			// Someone wants to know the GroupDispatcher for a group ID
			Get get = (Get) msg;
			ActorRef groupDispatcher = groupDispatcherMap.get(get.groupId);
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		} else if (msg instanceof GetOrCreate) {
			// Someone wants to know the GroupDispatcher for a group ID. If it
			// doesn't exist, create a new one.
			GetOrCreate getOrCreate = (GetOrCreate) msg;
			ActorRef groupDispatcher = groupDispatcherMap
					.get(getOrCreate.groupId);
			if (groupDispatcher == null) {
				groupDispatcher = Akka.system().actorOf(
						GroupDispatcher.props(self(), groupService,
								getOrCreate.groupId));
				groupDispatcherMap.put(getOrCreate.groupId, groupDispatcher);
			}
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		} else if (msg instanceof Unregister) {
			// A GroupDispatcher closed down and wants to unregister
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.groupId);
		} else if (msg instanceof PoisonSomeone) {
			// TODO 
			PoisonSomeone poison = (PoisonSomeone) msg;
			ActorRef actorRef = groupDispatcherMap
					.get(poison.idOfTheOneToPoison);
			if (actorRef != null) {
				actorRef.forward(msg, getContext());
			}
		} else {
			unhandled(msg);
		}
	}
}