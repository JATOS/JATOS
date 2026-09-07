package daos.common;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.db.jpa.JPAApi;

import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public abstract class AbstractDao {

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
