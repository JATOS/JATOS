package publix.akka;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;

import com.google.inject.Injector;
import common.Global;

/**
 * An actor producer that lets Google Guice create the Actor instances.
 */
public class GuiceActorProducer implements IndirectActorProducer {

	private final Injector injector;

	private final Class<Actor> actorType;

	public GuiceActorProducer(Injector injector, Class<Actor> actorType) {
		this.injector = injector;
		this.actorType = actorType;
	}

	@Override
	public Actor produce() {
		return Global.INJECTOR.getInstance(actorType);
	}

	@Override
	public Class<? extends Actor> actorClass() {
		return actorType;
	}
}
