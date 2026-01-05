package general.common;

import play.db.jpa.JPAApi;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A decorator implementation of the {@link JPAApi} interface that propagates the {@link EntityManager} instance within
 * the same thread using a {@link ThreadLocal}. This ensures that a single {@link EntityManager} instance can be reused
 * within the scope of a transaction when required.
 *
 * The class delegates the actual JPA operations to the underlying {@link JPAApi} implementation while managing the
 * thread-local context for the {@link EntityManager}.
 */
@Singleton
public class PropagatingJPAApi implements JPAApi {

    private final JPAApi delegate;
    private static final ThreadLocal<EntityManager> emContext = new ThreadLocal<>();

    public PropagatingJPAApi(JPAApi delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T withTransaction(String name, boolean readOnly, Function<EntityManager, T> block) {
        EntityManager em = emContext.get();
        if (em == null) {
            return delegate.withTransaction(name, readOnly, (innerEm) -> {
                try {
                    emContext.set(innerEm);
                    return block.apply(innerEm);
                } finally {
                    emContext.remove();
                }
            });
        } else {
            return block.apply(em);
        }
    }

    @Override
    public JPAApi start() {
        return delegate.start();
    }

    @Override
    public EntityManager em(String name) {
        return delegate.em(name);
    }

    @Override
    public <T> T withTransaction(Function<EntityManager, T> block) {
        return withTransaction("default", false, block);
    }

    @Override
    public <T> T withTransaction(String name, Function<EntityManager, T> block) {
        return withTransaction(name, false, block);
    }

    @Override
    public void withTransaction(Consumer<EntityManager> block) {
        withTransaction(em -> {
            block.accept(em);
            return null;
        });
    }

    @Override
    public void withTransaction(String name, Consumer<EntityManager> block) {
        withTransaction(name, false, block);
    }

    @Override
    public void withTransaction(String name, boolean readOnly, Consumer<EntityManager> block) {
        withTransaction(name, readOnly, em -> {
            block.accept(em);
            return null;
        });
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
