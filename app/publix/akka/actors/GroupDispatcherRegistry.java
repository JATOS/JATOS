package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import play.libs.Akka;
import publix.akka.messages.Get;
import publix.akka.messages.GetOrCreate;
import publix.akka.messages.ItsThisOne;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Unregister;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * @author Kristian Lange
 */
public class GroupDispatcherRegistry extends UntypedActor {

	// groupResultId -> GroupDispatcher
	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();

	public static Props props() {
		return Props.create(GroupDispatcherRegistry.class);
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
						GroupDispatcher.props(self(), getOrCreate.id));
				groupDispatcherMap.put(getOrCreate.id, groupDispatcher);
			}
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		} else if (msg instanceof Unregister) {
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.id);
		} else if (msg instanceof PoisonSomeone) {
			PoisonSomeone poison = (PoisonSomeone) msg;
			ActorRef actorRef = groupDispatcherMap
					.get(poison.idOfTheOneToPoison);
			if (actorRef != null) {
				actorRef.forward(msg, getContext());
			}
		}
	}
}