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
 * @author Kristian Lange
 */
@Singleton
public class GroupDispatcherRegistry extends UntypedActor {

	public static final String ACTOR_NAME = "GroupDispatcherRegistry";

	// groupResultId -> GroupDispatcher
	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();
	private final GroupService groupService;

	@Inject
	public GroupDispatcherRegistry(GroupService groupService) {
		this.groupService = groupService;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			Get get = (Get) msg;
			ActorRef groupDispatcher = groupDispatcherMap.get(get.id);
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		}
		if (msg instanceof GetOrCreate) {
			GetOrCreate getOrCreate = (GetOrCreate) msg;
			ActorRef groupDispatcher = groupDispatcherMap.get(getOrCreate.id);
			if (groupDispatcher == null) {
				groupDispatcher = Akka.system().actorOf(
						GroupDispatcher.props(self(), groupService,
								getOrCreate.id));
				groupDispatcherMap.put(getOrCreate.id, groupDispatcher);
			}
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		} else if (msg instanceof Unregister) {
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.groupResultId);
		} else if (msg instanceof PoisonSomeone) {
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