package daos.common;

import models.common.Batch;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for GroupResult
 *
 * @author Kristian Lange
 */
@Singleton
public class GroupResultDao extends AbstractDao {

    @Inject
    GroupResultDao(JPAApi jpa) {
        super(jpa);
    }

    public GroupResult persist(GroupResult groupResult) {
        super.persist(groupResult);
        return groupResult;
    }

    public GroupResult merge(GroupResult groupResult) {
        return super.merge(groupResult);
    }

    public void remove(GroupResult groupResult) {
        super.remove(groupResult);
    }

    public void refresh(GroupResult groupResult) {
        super.refresh(groupResult);
    }

    public GroupResult findById(Long id) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em.find(GroupResult.class, id));
    }

    public List<GroupResult> findAllByBatch(Batch batch) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT gr FROM GroupResult gr WHERE gr.batch=:batch";
            TypedQuery<GroupResult> query = em.createQuery(queryStr, GroupResult.class);
            return query.setParameter("batch", batch).getResultList();
        });
    }

    public Integer countByBatch(Batch batch) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT COUNT(gr) FROM GroupResult gr WHERE gr.batch=:batch";
            Query query = em.createQuery(queryStr).setParameter("batch", batch);
            Number result = (Number) query.getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Searches the database for GroupResults that fit the criteria: 1) are in the given batch, 2) are in state STARTED,
     * 3) where the activeMemberCount < Batch's maxActiveMembers, 3) activeMemberCount + historyMemberCount < Batch's
     * maxTotalMembers. Additionally the results are ordered by the activeMemberCount (highest first) and as a secondary
     * sorting criteria it orders by historyMemberCount (highest first).
     *
     * We use a PESSIMISTIC_WRITE lock to let GroupResults always have the current activeMemberCount and
     * historyMemberCount.
     */
    public List<GroupResult> findAllMaxNotReached(Batch batch) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT gr FROM GroupResult gr, Batch b "
                    + "WHERE gr.batch=:batch "
                    + "AND b.id=:batch "
                    + "AND gr.groupState=:groupState "
                    + "AND (b.maxActiveMembers is null OR gr.activeMemberCount < b.maxActiveMembers) "
                    + "AND (b.maxTotalMembers is null OR (gr.activeMemberCount + gr.historyMemberCount) < b.maxTotalMembers) "
                    + "ORDER BY gr.activeMemberCount DESC, gr.historyMemberCount DESC";
            TypedQuery<GroupResult> query = em.createQuery(queryStr, GroupResult.class);
            query.setParameter("batch", batch);
            query.setParameter("groupState", GroupState.STARTED);
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            return query.getResultList();
        });
    }

    /**
     * Returns a list of IDs of all StudyResults that are part of the active member list of the given GroupResult.
     */
    public List<Long> findAllActiveMemberIds(GroupResult groupResult) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT sr.id FROM StudyResult sr WHERE sr.activeGroupResult = :groupResult";
            return em.createQuery(queryStr, Long.class)
                    .setParameter("groupResult", groupResult)
                    .getResultList();
        });
    }

    /**
     * Returns a list of IDs of all StudyResults that are part of the history member list of the given GroupResult.
     */
    public List<Long> findAllHistoryMemberIds(GroupResult groupResult) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT sr.id FROM StudyResult sr WHERE sr.historyGroupResult = :groupResult";
            return em.createQuery(queryStr, Long.class)
                    .setParameter("groupResult", groupResult)
                    .getResultList();
        });
    }

}
