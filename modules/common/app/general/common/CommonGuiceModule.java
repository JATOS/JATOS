package general.common;

import com.google.inject.AbstractModule;
import play.db.jpa.*;
import com.google.inject.Provides;
import play.db.jpa.DefaultJPAApi;
import play.db.DBApi;
import play.inject.ApplicationLifecycle;
import play.libs.akka.AkkaGuiceSupport;
import utils.common.JsonObjectMapper;
import javax.inject.Singleton;

/**
 * Configuration of Guice dependency injection for Publix module
 * 
 * @author Kristian Lange
 */
public class CommonGuiceModule extends AbstractModule implements AkkaGuiceSupport {

    @Override
    protected void configure() {
        // Manually bind JPAConfig since we disabled JPAModule
        bind(JPAConfig.class).toProvider(DefaultJPAConfig.JPAConfigProvider.class).asEagerSingleton();
        
        bind(JsonObjectMapper.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public JPAApi provideJPAApi(JPAConfig jpaConfig, ApplicationLifecycle lifecycle, DBApi dbApi) {
        // Create the original implementation
        JPAApi defaultApi = new DefaultJPAApi(jpaConfig);
        
        lifecycle.addStopHook(() -> {
            defaultApi.shutdown();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });
        
        defaultApi.start();
        
        // Wrap it with our propagation logic
        return new PropagatingJPAApi(defaultApi);
    }
}
