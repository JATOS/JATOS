package daos.common;

import javax.inject.Singleton;
import javax.persistence.EntityManager;

import play.db.jpa.JPAApi;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Abstract DAO: Of the JPA calls only refresh() is public - persist(), merge()
 * and remove() are protected. The latter ones usually involve changes in
 * different entities and should be handled by the DAO of that type.
 *
 * @author Kristian Lange
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

    protected void merge(Object entity) {
        jpa.withTransaction(em -> {
            em.merge(entity);
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

}
