package daos.common;

import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * DAO for ComponentResult entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
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

    /**
     * Only update the 'data' field and leave all else untouched
     */
    public void updateData(Long id, String data) {
        jpa.em().createQuery("UPDATE ComponentResult cr SET cr.data = :data WHERE cr.id = :id")
                .setParameter("id", id)
                .setParameter("data", data)
                .executeUpdate();
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
        return result != null ? result.intValue() : 0;
    }

    /**
     * Returns the number of ComponentResults belonging to the given StudyResult.
     */
    public int countByStudyResult(StudyResult studyResult) {
        String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr WHERE cr.studyResult=:studyResult";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.setParameter("studyResult", studyResult).getSingleResult();
        return result != null ? result.intValue() : 0;
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

    /**
     * Returns data size (in Byte) that is occupied by the 'data' field of all component results belonging to the given
     * study.
     */
    public long sizeByStudy(Study study) {
        if (study.getComponentList().isEmpty()) return 0L;
        List<Long> componentIds = study.getComponentList().stream()
                .filter(Objects::nonNull)
                .map(Component::getId)
                .collect(Collectors.toList());
        Number result = (Number) jpa.em().createQuery(
                "SELECT SUM(LENGTH(data)) FROM ComponentResult WHERE component_id IN :componentIds")
                .setParameter("componentIds", componentIds)
                .getSingleResult();
        return result != null ? result.longValue() : 0L;
    }

    /**
     * Returns a list of component result IDs that belong to the given list of study result IDs. The order of the
     * study results is kept, e.g. if the study result IDs are sr1, sr2, sr3 - then in the returned list are first all
     * component result IDs of sr1, then all of sr2, and last all of sr3.
     */
    public List<Long> findIdsByStudyResultIds(List<Long> orderedSrids) {
        @SuppressWarnings("unchecked")
        List<Object[]> unorderedDbResults = jpa.em()
                .createNativeQuery("SELECT cr.studyResult_id, cr.id FROM ComponentResult cr "
                        + "WHERE cr.studyResult_id IN :ids")
                .setParameter("ids", orderedSrids)
                .getResultList();
        // We have to ensure that the order of the srids of the crids that will be returned is the same as the order of
        // the given srids.
        // This is a inefficient hack. We could use MySQL's "ORDER BY FIELD" (https://stackoverflow.com/questions/3799935)
        // - but it's not supported by H2.
        List<Long> orderedComponentResultIds = new ArrayList<>();
        for (Long orderedSrid : orderedSrids) {
            for (Object[] dbResult : unorderedDbResults) {
                long srid = ((Number) dbResult[0]).longValue();
                long crid = ((Number) dbResult[1]).longValue();
                if (srid == orderedSrid) {
                    orderedComponentResultIds.add(crid);
                }
            }
        }
        return orderedComponentResultIds;
    }

}
