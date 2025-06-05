package daos.common;

import general.common.Common;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import java.sql.Clob;
import java.sql.SQLException;
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

    private static final Logger.ALogger LOGGER = Logger.of(ComponentResultDao.class);

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
     * Overwrite data in 'data' fields (data, dataShort, dataSize)
     */
    public void replaceData(Long id, String data) {
        jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                        "SET cr.data = :data, " +
                        "cr.dataShort = SUBSTR(:data, 1, 1000), " +
                        "cr.dataSize = LENGTH(:data) " +
                        "WHERE cr.id = :id")
                .setParameter("id", id)
                .setParameter("data", data)
                .executeUpdate();
    }

    public void purgeData(Long id) {
        jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                        "SET cr.data = NULL, cr.dataShort = NULL, cr.dataSize = 0 " +
                        "WHERE cr.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    /**
     * Append data to 'data' field and replace data in 'dataShort' and 'dataSize'
     */
    public void appendData(Long id, String data) {
        if (Common.usesMysql()) {
            jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.data = CONCAT(COALESCE(cr.data, ''), :data), " +
                            "cr.dataShort = SUBSTR(cr.data, 1, 1000), " +
                            "cr.dataSize = LENGTH(cr.data) " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .setParameter("data", data)
                    .executeUpdate();
        } else {
            // H2 can't handle cr.data (it contains the old value)
            String oldData = getData(id);
            String newData = oldData != null ? oldData + data : data;
            jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.data = :newData, " +
                            "cr.dataShort = SUBSTR(:newData, 1, 1000), " +
                            "cr.dataSize = LENGTH(:newData) " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .setParameter("newData", newData)
                    .executeUpdate();
        }
    }

    /**
     * Only set the 'dataShort' and 'dataSize' field with data from 'data' (used only during update from an old version
     * of JATOS that didn't have those fields yet).
     */
    public void setDataSizeAndDataShort(Long id) {
        String data = getData(id);
        if (data != null) {
            jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.dataShort = SUBSTR(:data, 1, 1000), cr.dataSize = LENGTH(:data) " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .setParameter("data", data)
                    .executeUpdate();
        } else {
            jpa.em().createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.dataShort = NULL, cr.dataSize = 0 " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
        }
    }

    /**
     * Get 'data' field without fetching the whole row. The result is of a different type depending on the database
     * in use, MySQL or H2. So we have to treat them differently to get the String.
     */
    public String getData(Long id) {
        Object result = jpa.em().createNativeQuery("SELECT cr.data FROM ComponentResult cr WHERE cr.id = :id")
                .setParameter("id", id)
                .getSingleResult();
        // Performance-wise it would be better to pass on the stream but MySQL only returns String
        if (result instanceof String) {
            return (String) result;
        } else if (result instanceof Clob) {
            // H2 returns Clob
            Clob clob = (Clob) result;
            try {
                // From https://stackoverflow.com/a/63777729/1278769
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                LOGGER.error(".getData: Couldn't get data from ComponentResult " + id, e);
            }
        }
        return null;
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

    public List<ComponentResult> findByIds(List<Long> ids) {
        return jpa.em()
                .createQuery("SELECT cr FROM ComponentResult cr WHERE cr.id IN :ids", ComponentResult.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    public int count() {
        String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
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
     * Fetches all ComponentResults without 'dataSize' (is null). This is used only during update from an old version of
     * JATOS that didn't have those fields yet.
     */
    public List<Long> findAllIdsWhereDataSizeIsNull() {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.dataSize is NULL")
                .getResultList();
        return results.stream().map(r -> ((Number) r).longValue()).collect(Collectors.toList());
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
        // Added 'LEFT JOIN FETCH' for performance (loads LAZY-linked StudyResults and their Workers)
        return jpa.em()
                .createQuery("SELECT cr FROM ComponentResult cr " +
                        "LEFT JOIN FETCH cr.studyResult sr " +
                        "LEFT JOIN FETCH sr.worker " +
                        "WHERE cr.component=:component", ComponentResult.class)
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
        Number result = (Number) jpa.em().createNativeQuery(
                        "SELECT SUM(dataSize) FROM ComponentResult WHERE component_id IN :componentIds")
                .setParameter("componentIds", componentIds)
                .getSingleResult();
        return result != null ? result.longValue() : 0L;
    }

    public List<Long> findIdsByComponentIds(List<Long> componentIds) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component_id IN :componentIds")
                .setParameter("componentIds", componentIds)
                .getResultList();
        // Filter duplicate crids
        return results.stream().map(r -> ((Number) r).longValue()).distinct().collect(Collectors.toList());
    }

    public List<Long> findIdsByComponentUuids(List<String> componentUuids) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component_id IN " +
                        "(SELECT c.id FROM Component c WHERE c.uuid IN :componentUuids)")
                .setParameter("componentUuids", componentUuids)
                .getResultList();
        // Filter duplicate crids
        return results.stream().map(r -> ((Number) r).longValue()).distinct().collect(Collectors.toList());
    }

    public List<Long> findIdsByStudyIds(List<Long> studyIds) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component_id IN " +
                        "(SELECT c.id FROM Component c WHERE c.study_id IN :studyIds)")
                .setParameter("studyIds", studyIds)
                .getResultList();
        // Filter duplicate crids
        return results.stream().map(r -> ((Number) r).longValue()).distinct().collect(Collectors.toList());
    }

    public List<Long> findIdsByStudyUuids(List<String> studyUuids) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component_id IN " +
                        "(SELECT c.id FROM Component c WHERE c.study_id IN " +
                        "(SELECT s.id FROM Study s WHERE s.uuid IN :studyUuids))")
                .setParameter("studyUuids", studyUuids)
                .getResultList();
        // Filter duplicate crids
        return results.stream().map(r -> ((Number) r).longValue()).distinct().collect(Collectors.toList());
    }

    public List<Long> findIdsByStudyResultId(Long srid) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.studyResult_id = :srid")
                .setParameter("srid", srid)
                .getResultList();
        return results.stream().map(r -> ((Number) r).longValue()).collect(Collectors.toList());
    }

    /**
     * Returns a list of component result IDs that belong to the given list of study result IDs. The order of the
     * study results is kept, e.g., if the study result IDs are sr1, sr2, sr3 - then in the returned list are first all
     * component result IDs of sr1, then all of sr2, and last all of sr3.
     */
    public List<Long> findOrderedIdsByOrderedStudyResultIds(List<Long> orderedSrids) {
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

    /**
     * Takes a list component result IDs and checks if they exist in the database. Returns only the existing ones.
     */
    public List<Long> findIdsByComponentResultIds(List<Long> crids) {
        @SuppressWarnings("unchecked")
        List<Object> results = jpa.em()
                .createNativeQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.id IN :ids")
                .setParameter("ids", crids)
                .getResultList();
        return results.stream().map(r -> ((Number) r).longValue()).distinct().collect(Collectors.toList());
    }

}