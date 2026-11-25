package daos.common;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public abstract class AbstractDao {

    private final JpaTransactions transactions;

    protected AbstractDao(JpaTransactions transactions) {
        this.transactions = transactions;
    }

    protected void persist(Object entity) {
        transactions.withTransaction(em -> {
            em.persist(entity);
            return null;
        });
    }

    protected <T> T merge(T entity) {
        return transactions.withTransaction(em -> {
            return em.merge(entity);
        });
    }

    protected void remove(Object entity) {
        transactions.withTransaction(em -> {
            em.remove(entity);
            return null;
        });
    }

    protected void refresh(Object entity) {
        transactions.withTransaction(em -> {
            em.refresh(entity);
            return null;
        });
    }

    public void flush() {
        transactions.withTransaction(EntityManager::flush);
    }

    // Convenience helpers for DAO implementations; services coordinate transactions through the API.
    protected <T> T forReadOnly(Function<EntityManager, T> block) {
        return transactions.forReadOnly(block);
    }

    protected void forReadOnly(Consumer<EntityManager> block) {
        transactions.forReadOnly(block);
    }

    protected <T> T withTransaction(Function<EntityManager, T> block) {
        return transactions.withTransaction(block);
    }

    protected void withTransaction(Consumer<EntityManager> block) {
        transactions.withTransaction(block);
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
