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

    private final JpaTransactions jpa;

    protected AbstractDao(JpaTransactions jpa) {
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

    // Convenience helpers for DAO implementations; services coordinate transactions through the API.
    protected <T> T withReadOnlyTransaction(Function<EntityManager, T> block) {
        return jpa.withReadOnlyTransaction(block);
    }

    protected void withReadOnlyTransaction(Consumer<EntityManager> block) {
        jpa.withReadOnlyTransaction(block);
    }

    protected <T> T withTransaction(Function<EntityManager, T> block) {
        return jpa.withTransaction(block);
    }

    protected void withTransaction(Consumer<EntityManager> block) {
        jpa.withTransaction(block);
    }

    protected <T> T withNewTransaction(Function<EntityManager, T> block) {
        return jpa.withNewTransaction(block);
    }

    protected void withNewTransaction(Consumer<EntityManager> block) {
        jpa.withNewTransaction(block);
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
