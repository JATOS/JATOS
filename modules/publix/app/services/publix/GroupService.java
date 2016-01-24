package services.publix;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import models.common.Batch;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import models.common.StudyResult;

/**
 * Handles groups, e.g. joining or leaving a GroupResult. Members of a
 * GroupResult are StudyResults, which represents a particular study run.
 * 
 * All GroupResult members exchange messages via WebSockets that are called
 * group channels in JATOS. The message dispatching system is implemented with
 * Akka.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class GroupService {

	private final ResultCreator resultCreator;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;

	@Inject
	GroupService(ResultCreator resultCreator, StudyResultDao studyResultDao,
			GroupResultDao groupResultDao) {
		this.resultCreator = resultCreator;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	/**
	 * Checks whether this StudyResult has a GroupResult that is not in state
	 * FINISHED.
	 */
	public boolean hasUnfinishedGroupResult(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getGroupResult();
		return groupResult != null
				&& groupResult.getGroupState() != GroupState.FINISHED;
	}

	/**
	 * Joins the a GroupResult or create a new one. Persists changes.
	 */
	public GroupResult join(StudyResult studyResult, Batch batch) {
		// If we already have a GroupResult just return it
		if (hasUnfinishedGroupResult(studyResult)) {
			return studyResult.getGroupResult();
		}

		// TODO Check in history

		GroupResult groupResult = assignGroupResult(batch);
		groupResult.addActiveMember(studyResult);
		studyResult.setGroupResult(groupResult);
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
		return groupResult;
	}

	/**
	 * Look in the database if we have an incomplete GroupResult (state STARTED,
	 * maxActiveMember not reached, maxTotalMembers not reached). If there are
	 * more than one, return the one with the most active members. If there is
	 * none, create a new GroupResult.
	 */
	private GroupResult assignGroupResult(Batch batch) {
		GroupResult groupResult;
		List<GroupResult> groupResultList = groupResultDao
				.findAllMaxNotReached(batch);
		if (groupResultList.isEmpty()) {
			groupResult = resultCreator.createGroupResult(batch);
		} else {
			groupResult = groupResultList.stream()
					.max((gr1, gr2) -> Integer.compare(
							gr1.getActiveMemberList().size(),
							gr2.getActiveMemberList().size()))
					.get();
		}
		return groupResult;
	}

	/**
	 * Leaves the GroupResult this studyResult is member of.
	 */
	public void leave(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			return;
		}
		groupResult.removeActiveMember(studyResult);
		studyResult.setGroupResult(null);
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
	}

}
