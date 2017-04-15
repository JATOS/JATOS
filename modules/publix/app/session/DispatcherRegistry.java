package session;

import java.util.HashMap;
import java.util.Map;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import session.DispatcherRegistryProtocol.Get;
import session.DispatcherRegistryProtocol.GetOrCreate;
import session.DispatcherRegistryProtocol.ItsThisOne;
import session.DispatcherRegistryProtocol.Unregister;

/**
 * The DispatcherRegistry is an abstract Akka Actor. It keeps track of all
 * Dispatcher Actors.
 * 
 * @author Kristian Lange (2015)
 */
public abstract class DispatcherRegistry extends UntypedActor {

	/**
	 * Contains the dispatchers that are currently registered. Maps the an ID to
	 * the ActorRef.
	 */
	private final Map<Long, ActorRef> dispatcherMap = new HashMap<Long, ActorRef>();

	private final ActorSystem actorSystem;

	public DispatcherRegistry(ActorSystem actorSystem) {
		this.actorSystem = actorSystem;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			// Someone wants to know the Dispatcher to a certain ID
			tellDispatcher((Get) msg);
		} else if (msg instanceof GetOrCreate) {
			// Someone wants to know the Dispatcher to a certain ID
			// If it doesn't exist, create a new one.
			createAndTellDispatcher((GetOrCreate) msg);
		} else if (msg instanceof Unregister) {
			// A Dispatcher closed down and wants to unregister
			Unregister unregister = (Unregister) msg;
			dispatcherMap.remove(unregister.id);
		} else {
			unhandled(msg);
		}
	}

	private void tellDispatcher(Get get) {
		ActorRef dispatcher = dispatcherMap.get(get.id);
		ItsThisOne answer = new ItsThisOne(dispatcher);
		sender().tell(answer, self());
	}

	private void createAndTellDispatcher(GetOrCreate getOrCreate) {
		ActorRef dispatcher = dispatcherMap.get(getOrCreate.id);
		if (dispatcher == null) {
			dispatcher = actorSystem.actorOf(getProps(getOrCreate.id));
			dispatcherMap.put(getOrCreate.id, dispatcher);
		}
		ItsThisOne answer = new ItsThisOne(dispatcher);
		sender().tell(answer, self());
	}

	protected abstract Props getProps(long id);

}