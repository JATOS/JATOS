package daos.common;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.db.jpa.JPAApi;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.Arrays;

/**
 * Abstract DAO: Of the JPA calls, only refresh() is public - persist(), merge()
 * and remove() are protected. The latter ones usually involve changes in
 * different entities and should be handled by the DAO of that type.
 */
@Singleton
public abstract class AbstractDao {

    protected final JPAApi jpa;

    protected AbstractDao(JPAApi jpa) {
        this.jpa = jpa;
    }

    protected void persist(Object entity) {
        jpa.withTransaction(em -> {
            em.persist(entity);
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
        });
    }

    protected void refresh(Object entity) {
        jpa.withTransaction(em -> {
            em.refresh(entity);
        });
    }

	public void flush() {
        jpa.withTransaction(EntityManager::flush);
	}

    /**
     * Initialize all given objects that are loaded lazily in a Hibernate object
     */
    public static void initializeAndUnproxy(Object... objs) {
        Arrays.stream(objs).forEach(AbstractDao::initializeAndUnproxy);
    }

    /**
     * Initialize an object that is loaded lazily in a Hibernate object
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
