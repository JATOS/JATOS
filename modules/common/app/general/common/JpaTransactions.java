package general.common;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Singleton;
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
 * If REQUIRES_NEW propagation:
 * Any nested transaction call opens, commits, and rolls back.
 *
 * Limitations:
 * - Does not propagate transactions across threads or async boundaries.
 * - Inner transaction name/readOnly settings are ignored when a transaction is already active.
 * - It assumes only one "default" persistence unit.
 */
// @formatter:on
@Singleton
public class JpaTransactions implements JPAApi {

    public enum Propagation {
        JOIN_EXISTING,
        REQUIRES_NEW
    }

    private static final Logger.ALogger LOGGER = Logger.of(JpaTransactions.class);

    private final JPAApi delegate;
    private static final ThreadLocal<Deque<EntityManager>> emStack = ThreadLocal.withInitial(ArrayDeque::new);

    public JpaTransactions(JPAApi delegate) {
        this.delegate = delegate;
    }

    @FunctionalInterface
    public interface Cleanup {
        void run() throws Exception;
    }

    /**
     * Runs after a successful commit, including a joined outer transaction. Does not open a transaction. Capture
     * immutable values rather than lazy entities. Callback failures are logged, not propagated: the database has
     * already completed, and other callbacks must still be allowed to run.
     */
    public void afterCommit(String description, Cleanup cleanup) {
        afterCompletion(description, cleanup, () -> {});
    }

    /**
     * Registers callbacks on the active Hibernate transaction. REQUIRES_NEW callbacks belong to the inner transaction.
     * An unknown completion outcome is logged for manual recovery; it must not be treated as a rollback, since
     * restoring files in that case could contradict committed data.
     */
    public void afterCompletion(String description, Cleanup committed, Cleanup rolledBack) {
        EntityManager manager = emStack.get().peek();
        if (manager == null) throw new IllegalStateException("An active transaction is required");
        Transaction transaction = manager.unwrap(Session.class).getTransaction();
        if (!transaction.isActive()) throw new IllegalStateException("An active transaction is required");

        transaction.registerSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == Status.STATUS_COMMITTED) committed.run();
                    else if (status == Status.STATUS_ROLLEDBACK
                            // Hibernate 6 JDBC reports UNKNOWN after a successful rollback. The driver
                            // remains MARKED_ROLLBACK during this callback if beforeCompletion failed;
                            // otherwise it is NOT_ACTIVE. JDBC rollback failures do not invoke this callback.
                            || (status == Status.STATUS_UNKNOWN
                            && (transaction.getStatus() == TransactionStatus.NOT_ACTIVE
                                || transaction.getStatus() == TransactionStatus.MARKED_ROLLBACK)
                            && manager.unwrap(SessionImplementor.class).getTransactionCoordinator()
                            instanceof JdbcResourceLocalTransactionCoordinatorImpl))
                        rolledBack.run();
                    else LOGGER.error("Unknown transaction outcome (" + status + "): " + description
                                + ". Manual recovery may be required.");
                } catch (Exception e) {
                    LOGGER.error("Transaction completion callback failed: " + description
                            + ". Manual recovery may be required.", e);
                }
            }
        });
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

    /** Open a read-only scope, or join the current same-thread transaction. */
    public <T> T withReadOnlyTransaction(Function<EntityManager, T> block) {
        return withTransaction("default", true, block);
    }

    public void withReadOnlyTransaction(Consumer<EntityManager> block) {
        withTransaction("default", true, block);
    }

    /** Open an independent transaction, suspending any current same-thread transaction. */
    public <T> T withNewTransaction(Function<EntityManager, T> block) {
        return withTransaction(Propagation.REQUIRES_NEW, block);
    }

    public void withNewTransaction(Consumer<EntityManager> block) {
        withTransaction(Propagation.REQUIRES_NEW, block);
    }

    @Override
    public JPAApi start() {
        return delegate.start();
    }

    @Override
    public EntityManager em(String name) {
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
