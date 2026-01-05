package daos.common;

import models.common.Component;
import models.common.Study;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/**
 * DAO for Component entity
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentDao extends AbstractDao {

    @Inject
    ComponentDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(Component component) {
        super.persist(component);
    }

    public Component merge(Component component) {
        return super.merge(component);
    }

    /**
     * Change and persist active property of a Component.
     */
    public void changeActive(Component component, boolean active) {
        component.setActive(active);
        merge(component);
    }

    public void remove(Component component) {
        super.remove(component);
    }

    public Component findById(Long id) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em.find(Component.class, id));
    }

    /**
     * Finds the component by its ID and eagerly fetches the Study it belongs to.
     */
    public Component findByIdWithStudy(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c JOIN FETCH c.study WHERE c.id = :id";
            return em.createQuery(queryStr, Component.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds the component with this UUID
     */
    public Optional<Component> findByUuid(String uuid) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.uuid=:uuid";
            List<Component> componentList = em.createQuery(queryStr, Component.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();
            return !componentList.isEmpty() ? Optional.of(componentList.get(0)) : Optional.empty();
        });
    }

    /**
     * Searches for components with this UUID within the given study. This is faster than just searching by UUID since
     * Component does not have an index on its UUID field.
     */
    public Optional<Component> findByUuid(String uuid, Study study) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.study=:study AND c.uuid=:uuid";
            List<Component> componentList = em.createQuery(queryStr, Component.class)
                    .setParameter("study", study)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();
            return !componentList.isEmpty() ? Optional.of(componentList.get(0)) : Optional.empty();
        });
    }

    /**
     * Finds all components with the given title and returns them in a list. If there is none it returns an empty list.
     */
    public List<Component> findByTitle(String title) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.title=:title";
            TypedQuery<Component> query = em.createQuery(queryStr, Component.class);
            return query.setParameter("title", title).getResultList();
        });
    }

}
