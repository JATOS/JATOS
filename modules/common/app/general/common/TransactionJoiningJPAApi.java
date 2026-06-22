package general.common;

import play.db.jpa.JPAApi;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Function;

// @formatter:off
/**
 * JPAApi decorator that makes nested withTransaction calls join the current same-thread transaction.
 *
 * The outermost withTransaction call opens, commits, and rolls back the actual transaction. Any nested withTransaction
 * call on the same thread receives the same EntityManager and does not create an independent transaction.
 *
 * Limitations:
 * - Does not propagate transactions across threads or async boundaries.
 * - Inner transaction name/readOnly settings are ignored when a transaction is already active.
 * - Inner blocks cannot commit or roll back independently.
 * - It assumes only one "default" persistence unit.
 *
 */
// @formatter:on
@Singleton
public class TransactionJoiningJPAApi implements JPAApi {

    private final JPAApi delegate;
    private static final ThreadLocal<EntityManager> emContext = new ThreadLocal<>();

    public TransactionJoiningJPAApi(JPAApi delegate) {
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
        EntityManager em = emContext.get();
        return em != null ? em : delegate.em(name);
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
