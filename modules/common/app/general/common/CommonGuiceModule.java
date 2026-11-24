package general.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import json.common.DefaultJson;
import play.db.DBApi;
import play.db.jpa.DefaultJPAApi;
import play.db.jpa.DefaultJPAConfig;
import play.db.jpa.JPAConfig;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.pekko.PekkoGuiceSupport;

import javax.inject.Singleton;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Configuration of Guice dependency injection for Publix module
 */
public class CommonGuiceModule extends AbstractModule implements PekkoGuiceSupport {

    @Override
    protected void configure() {
        // Manually bind JPAConfig since we disabled JPAModule
        bind(JPAConfig.class).toProvider(DefaultJPAConfig.JPAConfigProvider.class).asEagerSingleton();
        
        bind(DefaultJson.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper(DefaultJson defaultJson) {
        ObjectMapper mapper = defaultJson.mapper();
        Json.setObjectMapper(mapper);
        return mapper;
    }

    @Provides
    @Singleton
    public JpaTransactions provideTransactions(JPAConfig jpaConfig, ApplicationLifecycle lifecycle, DBApi dbApi) {
        // Create the original implementation
        DefaultJPAApi defaultApi = new DefaultJPAApi(jpaConfig);

        lifecycle.addStopHook(() -> {
            defaultApi.shutdown();
            return completedFuture(null);
        });

        defaultApi.start();

        // Wrap it with our propagation logic
        return new JpaTransactions(defaultApi);
    }
}
