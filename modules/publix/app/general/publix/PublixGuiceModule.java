package general.publix;

import batch.BatchDispatcher;
import batch.BatchDispatcherRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import group.GroupDispatcher;
import group.GroupDispatcherRegistry;
import models.common.workers.*;
import play.libs.akka.AkkaGuiceSupport;
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
		// Config which Akka actors should be handled by Guice
		bindActor(GroupDispatcherRegistry.class, "group-dispatcher-registry-actor");
		bindActor(BatchDispatcherRegistry.class, "batch-dispatcher-registry-actor");
		bindActorFactory(BatchDispatcher.class, BatchDispatcher.Factory.class);
		bindActorFactory(GroupDispatcher.class, GroupDispatcher.Factory.class);
	}

}
