package actions.common;

import play.db.jpa.JPAApi;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

/**
 * The TransactionalAction class is responsible for managing database transactions
 * within the scope of a request. It ensures that the annotated actions or classes
 * are executed within a transactional context provided by JPA.
 *
 * Hint: In the Play Framework one cannot use jpa.em() directly. It always has to be wrapped "locally" in a
 * jpa.withTransaction(...) block.
 */
    public class TransactionalAction extends Action<TransactionalAction.Transactional> {

        public enum Mode { READ_WRITE, READ_ONLY }

        @With(TransactionalAction.class)
        @Target({ ElementType.TYPE, ElementType.METHOD })
        @Retention(RetentionPolicy.RUNTIME)
        public @interface Transactional {
            Mode value() default Mode.READ_WRITE;
        }

        private final JPAApi jpa;

        @Inject
        public TransactionalAction(JPAApi jpa) {
            this.jpa = jpa;
        }

        @Override
        public CompletionStage<Result> call(Http.Request req) {
            boolean readOnly = configuration != null && configuration.value() == Mode.READ_ONLY;
            return jpa.withTransaction("default", readOnly, em -> {
                return delegate.call(req);
            });
        }
    }
