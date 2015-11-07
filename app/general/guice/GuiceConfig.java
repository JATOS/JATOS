package general.guice;

import general.Global;
import groupservices.publix.akka.actors.GroupDispatcherRegistry;

import javax.inject.Named;
import javax.inject.Singleton;

import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import play.libs.Akka;
import services.publix.IStudyAuthorisation;
import services.publix.PublixUtils;
import services.publix.general_single.GeneralSinglePublixUtils;
import services.publix.general_single.GeneralSingleStudyAuthorisation;
import services.publix.jatos.JatosPublixUtils;
import services.publix.jatos.JatosStudyAuthorisation;
import services.publix.mt.MTPublixUtils;
import services.publix.mt.MTStudyAuthorisation;
import services.publix.personal_multiple.PersonalMultiplePublixUtils;
import services.publix.personal_multiple.PersonalMultipleStudyAuthorisation;
import services.publix.personal_single.PersonalSinglePublixUtils;
import services.publix.personal_single.PersonalSingleStudyAuthorisation;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;

/**
 * Initial configuration of Guice dependency injection
 * 
 * @author Kristian Lange (2015)
 */
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

	/**
	 * Initialize the guice injector in the Akka Guice Extension
	 */
	@Provides
	public ActorSystem actorSystem() {
		ActorSystem actorSystem = Akka.system();
		GuiceExtension.GuiceExtProvider.get(actorSystem).initialize(
				Global.INJECTOR);
		return actorSystem;
	}

	/**
	 * This GroupDispatcherRegistry actor is created when Guice is initialised.
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
