package utils.common;

import play.db.jpa.JPAApi;
import play.mvc.*;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class TransactionalAction extends Action.Simple {

    @With(TransactionalAction.class)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Transactional {
    }

    private final JPAApi jpa;

    @Inject
    public TransactionalAction(JPAApi jpa) {
        this.jpa = jpa;
    }

    @Override
    public CompletionStage<Result> call(Http.Request req) {
        return jpa.withTransaction((Function<EntityManager, CompletionStage<Result>>) em -> {
            return delegate.call(req);
        });
    }
}
