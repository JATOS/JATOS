package general.publix;

import batch.BatchDispatcher;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import group.GroupDispatcher;

/**
 * Configuration of Guice dependency injection for Publix module
 * 
 * @author Kristian Lange
 */
public class PublixGuiceModule extends AbstractModule {

	@Override
	protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(BatchDispatcher.class, BatchDispatcher.class)
                .build(BatchDispatcher.Factory.class)
        );
        install(new FactoryModuleBuilder()
                .implement(GroupDispatcher.class, GroupDispatcher.class)
                .build(GroupDispatcher.Factory.class)
        );
	}

}
