package daos.common;

import models.common.Component;
import models.common.Study;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
import java.util.List;

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
     * Searches for components with this UUID within the study with the given
     * ID.
     */
    public Component findByUuid(String uuid, Study study) {
        String queryStr = "SELECT c FROM Component c WHERE "
                + "c.uuid=:uuid and c.study=:study";
        TypedQuery<Component> query = jpa.em().createQuery(queryStr,
                Component.class);
        query.setParameter("uuid", uuid);
        query.setParameter("study", study);
        // There can be only one component with this UUID
        query.setMaxResults(1);
        List<Component> studyList = query.getResultList();
        return studyList.isEmpty() ? null : studyList.get(0);
    }

    /**
     * Finds all components with the given title and returns them in a list. If
     * there is none it returns an empty list.
     */
    public List<Component> findByTitle(String title) {
        String queryStr = "SELECT c FROM Component c WHERE " + "c.title=:title";
        TypedQuery<Component> query = jpa.em().createQuery(queryStr,
                Component.class);
        return query.setParameter("title", title).getResultList();
    }

}
