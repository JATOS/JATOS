package services.gui;

import com.google.common.base.Strings;
import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import models.gui.GroupSession;

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

    public GroupSession bindToGroupSession(GroupResult groupResult) {
        GroupSession groupSession = new GroupSession();
        groupSession.setVersion(groupResult.getGroupSessionVersion());
        groupSession.setData(groupResult.getGroupSessionData());
        return groupSession;
    }

    public boolean updateGroupSession(long groupResultId, GroupSession groupSession) {
        GroupResult currentGroupResult = groupResultDao.findById(groupResultId);
        if (currentGroupResult == null ||
                !groupSession.getVersion().equals(currentGroupResult.getGroupSessionVersion())) {
            return false;
        }

        currentGroupResult.setGroupSessionVersion(currentGroupResult.getGroupSessionVersion() + 1);
        if (Strings.isNullOrEmpty(groupSession.getData())) {
            currentGroupResult.setGroupSessionData("{}");
        } else {
            currentGroupResult.setGroupSessionData(groupSession.getData());
        }
        groupResultDao.update(currentGroupResult);
        return true;
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
