package general.common;

import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;
import utils.common.JsonObjectMapper;

/**
 * Configuration of Guice dependency injection for Publix module
 * 
 * @author Kristian Lange (2015)
 */
public class CommonGuiceModule extends AbstractModule implements AkkaGuiceSupport {

	@Override
	protected void configure() {
		bind(JsonObjectMapper.class).asEagerSingleton();
	}

}
