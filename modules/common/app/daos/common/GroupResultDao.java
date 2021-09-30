package daos.common;

import models.common.Batch;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for GroupResult
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class GroupResultDao extends AbstractDao {

    @Inject
    GroupResultDao(JPAApi jpa) {
        super(jpa);
    }

    public GroupResult create(GroupResult groupResult) {
        persist(groupResult);
        return groupResult;
    }

    public void update(GroupResult groupResult) {
        merge(groupResult);
    }

    public void remove(GroupResult groupResult) {
        super.remove(groupResult);
    }

    public void refresh(GroupResult groupResult) {
        super.refresh(groupResult);
    }

    public GroupResult findById(Long id) {
        return jpa.em().find(GroupResult.class, id);
    }

    public List<GroupResult> findAllByBatch(Batch batch) {
        String queryStr = "SELECT gr FROM GroupResult gr WHERE gr.batch=:batch";
        TypedQuery<GroupResult> query = jpa.em().createQuery(queryStr, GroupResult.class);
        return query.setParameter("batch", batch).getResultList();
    }

    public Integer countByBatch(Batch batch) {
        String queryStr = "SELECT COUNT(*) FROM GroupResult gr WHERE gr.batch=:batch";
        Query query = jpa.em().createQuery(queryStr).setParameter("batch", batch);
        Number result = (Number) query.getSingleResult();
        return result.intValue();
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
        String queryStr = "SELECT gr FROM GroupResult gr, Batch b "
                + "WHERE gr.batch=:batch "
                + "AND b.id=:batch "
                + "AND gr.groupState=:groupState "
                + "AND (b.maxActiveMembers is null OR gr.activeMemberCount < b.maxActiveMembers) "
                + "AND (b.maxTotalMembers is null OR (gr.activeMemberCount + gr.historyMemberCount) < b.maxTotalMembers) "
                + "ORDER BY gr.activeMemberCount DESC, gr.historyMemberCount DESC";
        TypedQuery<GroupResult> query = jpa.em().createQuery(queryStr, GroupResult.class);
        query.setParameter("batch", batch);
        query.setParameter("groupState", GroupState.STARTED);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

}
