package services.gui;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class for everything related to Groups.
 */
@Singleton
public class GroupService {

    private final GroupResultDao groupResultDao;

    @Inject
    GroupService(GroupResultDao groupResultDao) {
        this.groupResultDao = groupResultDao;
    }

    public GroupState toggleGroupFixed(GroupResult groupResult, boolean fixed) {
        if (fixed && groupResult.getGroupState() == GroupState.STARTED) {
            groupResult.setGroupState(GroupState.FIXED);
        } else if (!fixed && groupResult.getGroupState() == GroupState.FIXED) {
            groupResult.setGroupState(GroupState.STARTED);
        }
        groupResultDao.merge(groupResult);
        return groupResult.getGroupState();
    }

}
