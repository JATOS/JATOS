package daos.common;

import general.common.TransactionPropagatingJPAApi;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;
import play.Logger;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.db.jpa.JPAApi;

import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public abstract class AbstractDao {

    private static final Logger.ALogger LOGGER = Logger.of(AbstractDao.class);

    private final JPAApi jpa;

    protected AbstractDao(JPAApi jpa) {
        this.jpa = jpa;
    }

    protected void persist(Object entity) {
        jpa.withTransaction(em -> {
            em.persist(entity);
            return null;
        });
    }

    protected <T> T merge(T entity) {
        return jpa.withTransaction(em -> {
            return em.merge(entity);
        });
    }

    protected void remove(Object entity) {
        jpa.withTransaction(em -> {
            em.remove(entity);
            return null;
        });
    }

    protected void refresh(Object entity) {
        jpa.withTransaction(em -> {
            em.refresh(entity);
            return null;
        });
    }

    public void flush() {
        jpa.withTransaction(EntityManager::flush);
    }

    @FunctionalInterface
    public interface Cleanup {
        void run() throws Exception;
    }

    /**
     * Runs cleanup only after the currently active transaction commits, including a joined outer transaction.
     * Does not open a transaction. Capture IDs and paths rather than lazily loaded entities. Cleanup failures
     * are logged for manual recovery and do not prevent subsequent callbacks from running.
     */
    public void afterCommit(String description, Cleanup cleanup) {
        // Borrowed from the surrounding transaction; JPAApi owns and closes it.
        @SuppressWarnings("resource")
        EntityManager em = jpa.em("default");
        if (em == null) throw new IllegalStateException("afterCommit requires an active transaction");
        Transaction transaction = em.unwrap(Session.class).getTransaction();
        if (!transaction.isActive()) throw new IllegalStateException("afterCommit requires an active transaction");
        transaction.registerSynchronization(new Synchronization() {
            @Override public void beforeCompletion() {}

            @Override public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    try {
                        cleanup.run();
                    } catch (Exception e) {
                        LOGGER.error("Database committed, but cleanup failed: " + description + ". Manual cleanup may be required.", e);
                    }
                }
            }
        });
    }

    public <T> T withReadOnlyTransaction(Function<EntityManager, T> block) {
        return jpa.withTransaction("default", true, block);
    }

    public void withReadOnlyTransaction(Consumer<EntityManager> block) {
        jpa.withTransaction("default", true, em -> {
            block.accept(em);
            return null;
        });
    }

    public <T> T withTransaction(Function<EntityManager, T> block) {
        return jpa.withTransaction("default", false, block);
    }

    public void withTransaction(Consumer<EntityManager> block) {
        jpa.withTransaction("default", false, em -> {
            block.accept(em);
            return null;
        });
    }

    public <T> T withNewTransaction(Function<EntityManager, T> block) {
        return ((TransactionPropagatingJPAApi) jpa).withTransaction(
                TransactionPropagatingJPAApi.Propagation.REQUIRES_NEW, block);
    }

    public void withNewTransaction(Consumer<EntityManager> block) {
        ((TransactionPropagatingJPAApi) jpa).withTransaction(
                TransactionPropagatingJPAApi.Propagation.REQUIRES_NEW, block);
    }

    /**
     * Initialize an object loaded lazily in a Hibernate object
     */
    @SuppressWarnings("unchecked")
    public static <T> T initializeAndUnproxy(T obj) {
        Hibernate.initialize(obj);
        if (obj instanceof HibernateProxy) {
            obj = (T) ((HibernateProxy) obj).getHibernateLazyInitializer().getImplementation();
        }
        return obj;
    }

}
