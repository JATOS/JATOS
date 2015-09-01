package publix.akka;

import publix.akka.actors.GroupDispatcherRegistry;
import akka.actor.ActorRef;

import com.google.inject.Key;
import com.google.inject.name.Names;
import common.Global;

public class GuiceFactory {

	/**
	 * Return the actor by @Named annotation
	 *
	 * @return master supervisor Actor
	 */
	public static ActorRef getGroupDispatcherRegistry() {
		return Global.INJECTOR.getInstance(Key.get(ActorRef.class,
				Names.named(GroupDispatcherRegistry.ACTOR_NAME)));
	}

}
