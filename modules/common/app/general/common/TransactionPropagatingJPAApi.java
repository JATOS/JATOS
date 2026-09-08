package general.common;

import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Singleton;

import jakarta.persistence.EntityManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;

// @formatter:off
/**
 * JPAApi decorator that makes nested withTransaction calls
 * 1) join the current same-thread transaction (JOIN_EXISTING), or
 * 2) open a new transaction (REQUIRES_NEW).
 *
 * If JOIN_EXISTING propagation (default):
 * The outermost withTransaction call opens, commits, and rolls back the actual transaction. Any nested withTransaction
 * call on the same thread receives the same EntityManager and does not create an independent transaction.
 *
 * Ff REQUIRES_NEW propagation:
 * Any nested transaction call opens, commits, and rolls back.
 *
 * Limitations:
 * - Does not propagate transactions across threads or async boundaries.
 * - Inner transaction name/readOnly settings are ignored when a transaction is already active.
 * - It assumes only one "default" persistence unit.
 */
// @formatter:on
@Singleton
public class TransactionPropagatingJPAApi implements JPAApi {

    public enum Propagation {
        JOIN_EXISTING,
        REQUIRES_NEW
    }

    private static final Logger.ALogger LOGGER = Logger.of(TransactionPropagatingJPAApi.class);

    private final JPAApi delegate;
    private static final ThreadLocal<Deque<EntityManager>> emStack = ThreadLocal.withInitial(ArrayDeque::new);

    public TransactionPropagatingJPAApi(JPAApi delegate) {
        this.delegate = delegate;
    }

    /**
     * Standard Play JPAApi overload defaulting to JOIN_EXISTING propagation.
     */
    @Override
    public <T> T withTransaction(String name, boolean readOnly, Function<EntityManager, T> block) {
        return withTransaction(name, readOnly, Propagation.JOIN_EXISTING, block);
    }

    /**
     * Extended withTransaction supporting custom Propagation.
     */
    public <T> T withTransaction(String name, boolean readOnly, Propagation propagation, Function<EntityManager, T> block) {
        Deque<EntityManager> stack = emStack.get();
        EntityManager currentEm = stack.peek();

        if (propagation == Propagation.REQUIRES_NEW || currentEm == null) {
            LOGGER.debug(currentEm == null ? "No active transaction, creating new one" : "REQUIRES_NEW requested, suspending current transaction");
            return delegate.withTransaction(name, readOnly, innerEm -> {
                stack.push(innerEm);
                try {
                    return block.apply(innerEm);
                } finally {
                    stack.pop();
                    if (stack.isEmpty()) {
                        emStack.remove();
                    }
                }
            });
        } else {
            LOGGER.debug("Active transaction present, joining existing one");
            return block.apply(currentEm);
        }
    }

    public <T> T withTransaction(Propagation propagation, Function<EntityManager, T> block) {
        return withTransaction("default", false, propagation, block);
    }

    public void withTransaction(Propagation propagation, Consumer<EntityManager> block) {
        withTransaction("default", false, propagation, em -> {
            block.accept(em);
            return null;
        });
    }

    @Override
    public JPAApi start() {
        return delegate.start();
    }

    @Override
    public EntityManager em(String name) {
        @SuppressWarnings("resource")
        EntityManager em = emStack.get().peek();
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
