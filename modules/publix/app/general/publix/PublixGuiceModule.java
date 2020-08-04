package general.publix;

import batch.BatchDispatcher;
import batch.BatchDispatcherRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import group.GroupDispatcherRegistry;
import group.GroupDispatcher;
import models.common.workers.*;
import play.libs.akka.AkkaGuiceSupport;
import services.publix.PublixUtils;
import services.publix.StudyAuthorisation;
import services.publix.workers.*;

/**
 * Configuration of Guice dependency injection for Publix module
 * 
 * @author Kristian Lange (2015)
 */
public class PublixGuiceModule extends AbstractModule implements AkkaGuiceSupport {

	@Override
	protected void configure() {
		// Config Worker generics binding for IStudyAuthorisation
		bind(new TypeLiteral<StudyAuthorisation<JatosWorker>>() {
		}).to(JatosStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<PersonalSingleWorker>>() {
		}).to(PersonalSingleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<PersonalMultipleWorker>>() {
		}).to(PersonalMultipleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<GeneralSingleWorker>>() {
		}).to(GeneralSingleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<GeneralMultipleWorker>>() {
		}).to(GeneralMultipleStudyAuthorisation.class);
		bind(new TypeLiteral<StudyAuthorisation<MTWorker>>() {
		}).to(MTStudyAuthorisation.class);

		// Config which Akka actors should be handled by Guice
		bindActor(GroupDispatcherRegistry.class, "group-dispatcher-registry-actor");
		bindActor(BatchDispatcherRegistry.class, "batch-dispatcher-registry-actor");
		bindActorFactory(BatchDispatcher.class, BatchDispatcher.Factory.class);
		bindActorFactory(GroupDispatcher.class, GroupDispatcher.Factory.class);
	}

}
