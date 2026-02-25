package services.gui;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import models.gui.BatchOrGroupSession;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class GroupService {

    private final GroupResultDao groupResultDao;

    @Inject
    GroupService(GroupResultDao groupResultDao) {
        this.groupResultDao = groupResultDao;
    }

    public BatchOrGroupSession bindToGroupSession(GroupResult groupResult) {
        BatchOrGroupSession groupSession = new BatchOrGroupSession();
        groupSession.setVersion(groupResult.getGroupSessionVersion());
        groupSession.setSessionData(groupResult.getGroupSessionData());
        return groupSession;
    }

    public GroupState toggleGroupFixed(GroupResult groupResult, boolean fixed) {
        if (fixed && groupResult.getGroupState() == GroupState.STARTED) {
            groupResult.setGroupState(GroupState.FIXED);
        } else if (!fixed && groupResult.getGroupState() == GroupState.FIXED) {
            groupResult.setGroupState(GroupState.STARTED);
        }
        groupResultDao.update(groupResult);
        return groupResult.getGroupState();
    }

}
