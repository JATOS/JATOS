package general.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import play.db.jpa.*;
import com.google.inject.Provides;
import play.db.jpa.DefaultJPAApi;
import play.db.DBApi;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.akka.AkkaGuiceSupport;
import json.common.DefaultJson;

import javax.inject.Singleton;

import static java.util.concurrent.CompletableFuture.*;

/**
 * Configuration of Guice dependency injection for Publix module
 */
public class CommonGuiceModule extends AbstractModule implements AkkaGuiceSupport {

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
    public JPAApi provideJPAApi(JPAConfig jpaConfig, ApplicationLifecycle lifecycle, DBApi dbApi) {
        // Create the original implementation
        JPAApi defaultApi = new DefaultJPAApi(jpaConfig);

        lifecycle.addStopHook(() -> {
            defaultApi.shutdown();
            return completedFuture(null);
        });

        defaultApi.start();

        // Wrap it with our propagation logic
        return new TransactionJoiningJPAApi(defaultApi);
    }
}
