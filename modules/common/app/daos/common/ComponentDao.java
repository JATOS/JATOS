package daos.common;

import models.common.Component;
import models.common.Study;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/**
 * DAO for Component entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class ComponentDao extends AbstractDao {

    @Inject
    ComponentDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(Component component) {
        persist(component);
    }

    public void update(Component component) {
        merge(component);
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
        return jpa.em().find(Component.class, id);
    }

    /**
     * Finds the component with this UUID
     */
    public Optional<Component> findByUuid(String uuid) {
        String queryStr = "SELECT c FROM Component c WHERE c.uuid=:uuid";
        List<Component> componentList = jpa.em().createQuery(queryStr, Component.class)
                .setParameter("uuid", uuid)
                .setMaxResults(1)
                .getResultList();
        return !componentList.isEmpty() ? Optional.of(componentList.get(0)) : Optional.empty();
    }

    /**
     * Searches for components with this UUID within the given study. This is faster than just searching by UUID since
     * Component does not have an index on its UUID field.
     */
    public Optional<Component> findByUuid(String uuid, Study study) {
        String queryStr = "SELECT c FROM Component c WHERE c.study=:study AND c.uuid=:uuid";
        List<Component> componentList = jpa.em().createQuery(queryStr, Component.class)
                .setParameter("uuid", uuid)
                .setParameter("study", study)
                .setMaxResults(1)
                .getResultList();
        return !componentList.isEmpty() ? Optional.of(componentList.get(0)) : Optional.empty();
    }

    /**
     * Finds all components with the given title and returns them in a list. If
     * there is none it returns an empty list.
     */
    public List<Component> findByTitle(String title) {
        String queryStr = "SELECT c FROM Component c WHERE c.title=:title";
        TypedQuery<Component> query = jpa.em().createQuery(queryStr, Component.class);
        return query.setParameter("title", title).getResultList();
    }

}
