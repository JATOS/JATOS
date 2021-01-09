package daos.common;

import models.common.Component;
import models.common.ComponentResult;
import models.common.StudyResult;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for ComponentResult entity
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultDao extends AbstractDao {

    @Inject
    ComponentResultDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(ComponentResult componentResult) {
        persist(componentResult);
    }

    public void update(ComponentResult componentResult) {
        merge(componentResult);
    }

    public void remove(ComponentResult componentResult) {
        super.remove(componentResult);
    }

    public void refresh(ComponentResult componentResult) {
        super.refresh(componentResult);
    }

    public ComponentResult findById(Long id) {
        return jpa.em().find(ComponentResult.class, id);
    }

    /**
     * Returns the number of ComponentResults belonging to the given Component.
     */
    public int countByComponent(Component component) {
        String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr WHERE cr.component=:component";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.setParameter("component", component).getSingleResult();
        return result.intValue();
    }

    /**
     * Returns the number of ComponentResults belonging to the given StudyResult.
     */
    public int countByStudyResult(StudyResult studyResult) {
        String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr WHERE cr.studyResult=:studyResult";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.setParameter("studyResult", studyResult).getSingleResult();
        return result.intValue();
    }

    public List<ComponentResult> findAllByComponent(Component component) {
        return jpa.em()
                .createQuery("SELECT cr FROM ComponentResult cr WHERE cr.component=:component", ComponentResult.class)
                .setParameter("component", component)
                .getResultList();
    }

    /**
     * Returns paginated ComponentResult that belong to the given Component
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<ComponentResult> findAllByComponent(Component component, int first, int max) {
        return jpa.em()
                .createQuery("SELECT cr FROM ComponentResult cr WHERE cr.component=:component", ComponentResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("component", component)
                .getResultList();
    }

}
