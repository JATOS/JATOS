package general.publix;

import batch.BatchDispatcher;
import batch.BatchDispatcherRegistry;
import com.google.inject.AbstractModule;
import group.GroupDispatcher;
import group.GroupDispatcherRegistry;
import play.libs.akka.AkkaGuiceSupport;

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
