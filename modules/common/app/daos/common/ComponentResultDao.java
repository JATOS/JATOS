package daos.common;

import general.common.Common;
import models.common.*;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DAO for ComponentResult entity
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultDao extends AbstractDao {

    private static final Logger.ALogger LOGGER = Logger.of(ComponentResultDao.class);

    @Inject
    ComponentResultDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(ComponentResult componentResult) {
        super.persist(componentResult);
    }

    public ComponentResult merge(ComponentResult componentResult) {
        return super.merge(componentResult);
    }

    /**
     * Overwrite data in 'data' fields (data, dataShort, dataSize)
     */
    public void replaceData(Long id, String data) {
        jpa.withTransaction(em -> {
            em.createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.data = :data, " +
                            "cr.dataShort = SUBSTR(:data, 1, 1000), " +
                            "cr.dataSize = LENGTH(:data) " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .setParameter("data", data)
                    .executeUpdate();
        });
    }

    public void purgeData(Long id) {
        jpa.withTransaction(em -> {
            em.createNativeQuery("UPDATE ComponentResult cr " +
                            "SET cr.data = NULL, cr.dataShort = NULL, cr.dataSize = 0 " +
                            "WHERE cr.id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
        });
    }

    /**
     * Append data to 'data' field and replace data in 'dataShort' and 'dataSize'
     */
    public void appendData(Long id, String data) {
        jpa.withTransaction(em -> {
            if (Common.usesMysql()) {
                em.createNativeQuery("UPDATE ComponentResult cr " +
                                "SET cr.data = CONCAT(COALESCE(cr.data, ''), :data), " +
                                "cr.dataShort = SUBSTR(cr.data, 1, 1000), " +
                                "cr.dataSize = LENGTH(cr.data) " +
                                "WHERE cr.id = :id")
                        .setParameter("id", id)
                        .setParameter("data", data)
                        .executeUpdate();
            } else {
                // H2 can't handle cr.data (it contains the old value) - fetch existing data first
                Object result = em.createNativeQuery("SELECT cr.data FROM ComponentResult cr WHERE cr.id = :id")
                        .setParameter("id", id)
                        .getSingleResult();

                String oldData = readDataColumnAsString(result);
                String newData = oldData != null ? oldData + data : data;
                em.createNativeQuery("UPDATE ComponentResult cr " +
                                "SET cr.data = :newData, " +
                                "cr.dataShort = SUBSTR(:newData, 1, 1000), " +
                                "cr.dataSize = LENGTH(:newData) " +
                                "WHERE cr.id = :id")
                        .setParameter("id", id)
                        .setParameter("newData", newData)
                        .executeUpdate();
            }
        });
    }

    /**
     * Converts a DB result that can be either a String (e.g. MySQL) or a Clob (e.g. H2) into a String. Returns null if
     * the result is null or conversion fails.
     */
    private String readDataColumnAsString(Object result) {
        if (result == null) return null;

        // Performance-wise it would be better to pass on the stream, but MySQL only returns String. H2 returns Clob.
        if (result instanceof String) {
            return (String) result;
        } else if (result instanceof Clob) {
            Clob clob = (Clob) result;
            try {
                // From https://stackoverflow.com/a/63777729/1278769
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                LOGGER.error(".readDataColumnAsString: Couldn't read CLOB", e);
                return null;
            }
        } else {
            LOGGER.warn(".readDataColumnAsString: Unexpected type " + result.getClass().getName());
            return null;
        }
    }

    /**
     * Only set the 'dataShort' and 'dataSize' field with data from 'data' (used only during update from an old version
     * of JATOS that didn't have those fields yet).
     */
    public void setDataSizeAndDataShort(Long id) {
        jpa.withTransaction(em -> {
            Object result = em.createNativeQuery("SELECT cr.data FROM ComponentResult cr WHERE cr.id = :id")
                    .setParameter("id", id)
                    .getSingleResult();

            String data = readDataColumnAsString(result);
            if (data != null) {
                em.createNativeQuery("UPDATE ComponentResult cr " +
                                "SET cr.dataShort = SUBSTR(:data, 1, 1000), cr.dataSize = LENGTH(:data) " +
                                "WHERE cr.id = :id")
                        .setParameter("id", id)
                        .setParameter("data", data)
                        .executeUpdate();
            } else {
                em.createQuery("UPDATE ComponentResult cr " +
                                "SET cr.dataShort = NULL, cr.dataSize = 0 " +
                                "WHERE cr.id = :id")
                        .setParameter("id", id)
                        .executeUpdate();
            }
        });
    }

    /**
     * Get the 'data' field without fetching the whole row. The result is of a different type depending on the database
     * in use, MySQL or H2. So we have to treat them differently to get the String.
     */
    public String getData(Long id) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> {
            Object result = em.createNativeQuery("SELECT cr.data FROM ComponentResult cr WHERE cr.id = :id")
                    .setParameter("id", id)
                    .getSingleResult();
            return readDataColumnAsString(result);
        });
    }

    public void remove(ComponentResult componentResult) {
        super.remove(componentResult);
    }

    public void refresh(ComponentResult componentResult) {
        super.refresh(componentResult);
    }

    public ComponentResult findById(Long id) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em.find(ComponentResult.class, id));
    }

    /**
     * Finds a componentResult by its ID and eagerly fetches the componet to avoid LazyInitializationException.
     */
    public ComponentResult findByIdWithComponent(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT cr FROM ComponentResult cr LEFT JOIN FETCH cr.component WHERE cr.id = :id";
            return em.createQuery(queryStr, ComponentResult.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    public List<ComponentResult> findByIds(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em
                .createQuery("SELECT cr FROM ComponentResult cr WHERE cr.id IN :ids", ComponentResult.class)
                .setParameter("ids", ids)
                .getResultList());
    }

    public int count() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr";
            Query query = em.createQuery(queryStr);
            Number result = (Number) query.getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of ComponentResults belonging to the given Component.
     */
    public int countByComponent(Component component) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr WHERE cr.component=:component";
            Query query = em.createQuery(queryStr);
            Number result = (Number) query.setParameter("component", component).getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Fetches all ComponentResults without 'dataSize' (is null). This is used only during update from an old version of
     * JATOS that didn't have those fields yet.
     */
    public List<Long> findAllIdsWhereDataSizeIsNull() {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.dataSize is NULL", Long.class)
                .getResultList());
    }

    public List<ComponentResult> findAllByComponent(Component component) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em
                .createQuery("SELECT cr FROM ComponentResult cr WHERE cr.component=:component", ComponentResult.class)
                .setParameter("component", component)
                .getResultList());
    }

    /**
     * Returns paginated ComponentResult that belong to the given Component
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<ComponentResult> findAllByComponent(Component component, int first, int max) {
        // Added 'LEFT JOIN FETCH' for performance (loads LAZY-linked StudyResults and their Workers)
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em
                .createQuery("SELECT cr FROM ComponentResult cr " +
                                "LEFT JOIN FETCH cr.studyResult sr " +
                                "LEFT JOIN FETCH sr.worker " +
                                "WHERE cr.component=:component " +
                                "ORDER BY cr.id ASC",
                        ComponentResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("component", component)
                .getResultList());
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
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery(
                            "SELECT SUM(cr.dataSize) FROM ComponentResult cr WHERE cr.component.id IN :componentIds")
                    .setParameter("componentIds", componentIds)
                    .getSingleResult();
            return result != null ? result.longValue() : 0L;
        });
    }

    public List<Long> findIdsByComponentIds(List<Long> componentIds) {
        if (componentIds.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component.id IN :componentIds", Long.class)
                .setParameter("componentIds", componentIds)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsByComponentUuids(List<String> componentUuids) {
        if (componentUuids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component.id IN " +
                        "(SELECT c.id FROM Component c WHERE c.uuid IN :componentUuids)", Long.class)
                .setParameter("componentUuids", componentUuids)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsByStudyIds(List<Long> studyIds) {
        if (studyIds.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component.id IN " +
                        "(SELECT c.id FROM Component c WHERE c.study.id IN :studyIds)", Long.class)
                .setParameter("studyIds", studyIds)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsByStudyUuids(List<String> studyUuids) {
        if (studyUuids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.component.id IN " +
                        "(SELECT c.id FROM Component c WHERE c.study.id IN " +
                        "(SELECT s.id FROM Study s WHERE s.uuid IN :studyUuids))", Long.class)
                .setParameter("studyUuids", studyUuids)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsByStudyResultId(Long srid) {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.studyResult.id = :srid", Long.class)
                .setParameter("srid", srid)
                .getResultList());
    }

    /**
     * Returns the last ComponentResult that belongs to the given StudyResult.
     */
    public Optional<ComponentResult> findLastByStudyResult(StudyResult studyResult) {
        return jpa.withTransaction(em -> {
            String queryStr = "SELECT cr FROM ComponentResult cr WHERE cr.studyResult = :studyResult ORDER BY cr.id DESC";
            TypedQuery<ComponentResult> query = em.createQuery(queryStr, ComponentResult.class);
            query.setParameter("studyResult", studyResult);
            query.setMaxResults(1);
            List<ComponentResult> results = query.getResultList();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        });
    }

    /**
     * Returns a list of component result IDs that belong to the given study result and
     * optionally a specific component.
     */
    public List<Long> findIdsByStudyResultAndComponent(Long studyResultId, Component component) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT cr.id FROM ComponentResult cr WHERE cr.studyResult.id = :srid";
            if (component != null) {
                queryStr += " AND cr.component = :component";
            }
            TypedQuery<Long> query = em.createQuery(queryStr, Long.class);
            query.setParameter("srid", studyResultId);
            if (component != null) {
                query.setParameter("component", component);
            }
            return query.getResultList();
        });
    }

    /**
     * Returns a list of component result IDs that belong to the given list of study result IDs. The order of the study
     * results is kept, e.g., if the study result IDs are sr1, sr2, sr3 - then in the returned list are first all
     * component result IDs of sr1, then all of sr2, and last all of sr3.
     */
    public List<Long> findOrderedIdsByOrderedStudyResultIds(List<Long> orderedSrids) {
        if (orderedSrids.isEmpty()) return Collections.emptyList();
        List<Object[]> unorderedDbResults = jpa.withTransaction((javax.persistence.EntityManager em) ->
                em.createQuery("SELECT cr.studyResult.id, cr.id FROM ComponentResult cr "
                        + "WHERE cr.studyResult.id IN :ids", Object[].class)
                .setParameter("ids", orderedSrids)
                .getResultList());
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
        if (crids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT cr.id FROM ComponentResult cr WHERE cr.id IN :ids", Long.class)
                .setParameter("ids", crids)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public void setQuotaReached(Long componentResultId) {
        jpa.withTransaction(em -> {
            em.createQuery("UPDATE ComponentResult cr SET cr.quotaReached = true WHERE cr.id = :id")
                    .setParameter("id", componentResultId)
                    .executeUpdate();
        });
    }

}