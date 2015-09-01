package common;

import models.workers.GeneralSingleWorker;
import models.workers.JatosWorker;
import models.workers.MTWorker;
import models.workers.PersonalMultipleWorker;
import models.workers.PersonalSingleWorker;
import play.libs.Akka;
import publix.akka.GuiceExtension;
import publix.akka.actors.GroupDispatcherRegistry;
import publix.services.IStudyAuthorisation;
import publix.services.PublixUtils;
import publix.services.general_single.GeneralSinglePublixUtils;
import publix.services.general_single.GeneralSingleStudyAuthorisation;
import publix.services.jatos.JatosPublixUtils;
import publix.services.jatos.JatosStudyAuthorisation;
import publix.services.mt.MTPublixUtils;
import publix.services.mt.MTStudyAuthorisation;
import publix.services.personal_multiple.PersonalMultiplePublixUtils;
import publix.services.personal_multiple.PersonalMultipleStudyAuthorisation;
import publix.services.personal_single.PersonalSinglePublixUtils;
import publix.services.personal_single.PersonalSingleStudyAuthorisation;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

public class GuiceConfig extends AbstractModule {

	@Override
	protected void configure() {
		bind(new TypeLiteral<IStudyAuthorisation<GeneralSingleWorker>>() {
		}).to(GeneralSingleStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<JatosWorker>>() {
		}).to(JatosStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<MTWorker>>() {
		}).to(MTStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<PersonalMultipleWorker>>() {
		}).to(PersonalMultipleStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<PersonalSingleWorker>>() {
		}).to(PersonalSingleStudyAuthorisation.class);

		bind(new TypeLiteral<PublixUtils<GeneralSingleWorker>>() {
		}).to(GeneralSinglePublixUtils.class);
		bind(new TypeLiteral<PublixUtils<JatosWorker>>() {
		}).to(JatosPublixUtils.class);
		bind(new TypeLiteral<PublixUtils<MTWorker>>() {
		}).to(MTPublixUtils.class);
		bind(new TypeLiteral<PublixUtils<PersonalMultipleWorker>>() {
		}).to(PersonalMultiplePublixUtils.class);
		bind(new TypeLiteral<PublixUtils<PersonalSingleWorker>>() {
		}).to(PersonalSinglePublixUtils.class);
	}

	@Provides
	public ActorSystem actorSystem() {
		ActorSystem actorSystem = Akka.system();
		// initialize the guice injector in the Akka Guice Extension.
		GuiceExtension.GuiceExtProvider.get(actorSystem).initialize(
				Global.INJECTOR);
		return actorSystem;
	}

	/**
	 * This actor is created so that actor system gets initialized with all the
	 * actors.
	 *
	 * @param actorSystem
	 * @return
	 */
	@Provides
	@Named(GroupDispatcherRegistry.ACTOR_NAME)
	@Singleton
	public ActorRef groupDispatcherRegistry(ActorSystem actorSystem) {
		return actorSystem.actorOf(
				GuiceExtension.GuiceExtProvider.get(actorSystem).props(
						GroupDispatcherRegistry.class),
				GroupDispatcherRegistry.ACTOR_NAME);
	}
}
