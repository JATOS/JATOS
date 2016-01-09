package general.publix;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import play.libs.akka.AkkaGuiceSupport;
import services.publix.StudyAuthorisation;
import services.publix.PublixUtils;
import services.publix.akka.actors.GroupDispatcherRegistry;
import services.publix.workers.GeneralSinglePublixUtils;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import services.publix.workers.JatosPublixUtils;
import services.publix.workers.JatosStudyAuthorisation;
import services.publix.workers.MTPublixUtils;
import services.publix.workers.MTStudyAuthorisation;
import services.publix.workers.PersonalMultiplePublixUtils;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import services.publix.workers.PersonalSinglePublixUtils;
import services.publix.workers.PersonalSingleStudyAuthorisation;

/**
 * Configuration of Guice dependency injection for Publix module
 * 
 * @author Kristian Lange (2015)
 */
public class PublixGuiceModule extends AbstractModule implements AkkaGuiceSupport {

	@Override
	protected void configure() {
		// Config Worker generics binding for IStudyAuthorisation
		bind(new TypeLiteral<StudyAuthorisation<GeneralSingleWorker>>() {
		}).to(GeneralSingleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<JatosWorker>>() {
		}).to(JatosStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<MTWorker>>() {
		}).to(MTStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<PersonalMultipleWorker>>() {
		}).to(PersonalMultipleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<PersonalSingleWorker>>() {
		}).to(PersonalSingleStudyAuthorisation.class);

		// Config Worker generics binding for PublixUtils
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

		// Config which Akka actors should be handled by Guice
		bindActor(GroupDispatcherRegistry.class, "group-dispatcher-registry-actor");
	}

}
