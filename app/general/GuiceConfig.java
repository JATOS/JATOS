package general;

import com.google.inject.AbstractModule;

import general.common.Common;
import play.libs.akka.AkkaGuiceSupport;

/**
 * Initial configuration of Guice dependency injection
 * 
 * @author Kristian Lange (2015)
 */
public class GuiceConfig extends AbstractModule implements AkkaGuiceSupport {

	@Override
	protected void configure() {
		// JATOS startup initialisation (eager -> called during JATOS start)
		bind(Common.class).asEagerSingleton();
		bind(Initializer.class).asEagerSingleton();
		bind(OnStartStop.class).asEagerSingleton();
	}

}
