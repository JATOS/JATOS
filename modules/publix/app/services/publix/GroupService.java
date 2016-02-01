package services.publix;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.NoContentPublixException;
import models.common.Batch;
import models.common.GroupResult;
import models.common.StudyResult;
import play.db.jpa.JPA;

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
	private final PublixErrorMessages errorMessages;

	@Inject
	GroupService(ResultCreator resultCreator, StudyResultDao studyResultDao,
			GroupResultDao groupResultDao, PublixErrorMessages errorMessages) {
		this.resultCreator = resultCreator;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
		this.errorMessages = errorMessages;
	}

	/**
	 * Checks whether this StudyResult has an history GroupResult, means it was
	 * a member in a group in the past and it tries to run a second group study.
	 */
	public void checkHistoryGroupResult(StudyResult studyResult)
			throws ForbiddenPublixException {
		GroupResult groupResult = studyResult.getHistoryGroupResult();
		if (groupResult != null) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.GROUP_STUDY_NOT_POSSIBLE_TWICE);
		}
	}

	/**
	 * Joins the a GroupResult or create a new one. Persists changes.
	 * 
	 * Looks in the database whether we have an incomplete GroupResult (state
	 * STARTED, maxActiveMember not reached, maxTotalMembers not reached). If
	 * there are more than one, return the one with the most active members. If
	 * there is none, create a new GroupResult.
	 */
	public GroupResult join(StudyResult studyResult, Batch batch) {
		List<GroupResult> groupResultList = groupResultDao
				.findAllMaxNotReached(batch);
		GroupResult groupResult;
		if (groupResultList.isEmpty()) {
			groupResult = resultCreator.createGroupResult(batch);
		} else {
			groupResult = findGroupResultWithMostActiveMembers(groupResultList);
		}
		groupResult.addActiveMember(studyResult);
		studyResult.setActiveGroupResult(groupResult);
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
		return groupResult;
	}

	/**
	 * Reassigns this StudyResult to a different GroupResult if possible.
	 * Persists changes.
	 * 
	 * Looks in the database whether we have other incomplete GroupResult (state
	 * STARTED, maxActiveMember not reached, maxTotalMembers not reached). If
	 * there are more than one, it returns the one with the most active members.
	 * If there is no other GroupResult it throws a NoContentPublixException.
	 */
	public GroupResult reassign(StudyResult studyResult, Batch batch)
			throws NoContentPublixException {
		GroupResult currentGroupResult = studyResult.getActiveGroupResult();
		List<GroupResult> groupResultList = groupResultDao
				.findAllMaxNotReached(batch);
		groupResultList.remove(currentGroupResult);
		if (groupResultList.isEmpty()) {
			// No other possible group result
			throw new NoContentPublixException(errorMessages
					.groupNotFoundForReassigning(studyResult.getId()));
		}
		GroupResult differentGroupResult = findGroupResultWithMostActiveMembers(
				groupResultList);
		currentGroupResult.removeActiveMember(studyResult);
		differentGroupResult.addActiveMember(studyResult);
		studyResult.setActiveGroupResult(differentGroupResult);
		groupResultDao.update(currentGroupResult);
		groupResultDao.update(differentGroupResult);
		studyResultDao.update(studyResult);
		return differentGroupResult;
	}

	/**
	 * Finds the group result with the most active members. Assumes the given
	 * list is not empty.
	 */
	private GroupResult findGroupResultWithMostActiveMembers(
			List<GroupResult> groupResultList) {
		if (groupResultList.size() == 1) {
			return groupResultList.get(0);
		}
		GroupResult newGroupResult = groupResultList.stream()
				.max((gr1, gr2) -> Integer.compare(
						gr1.getActiveMemberList().size(),
						gr2.getActiveMemberList().size()))
				.get();
		return newGroupResult;
	}

	/**
	 * Leaves the group that this studyResult is member of.
	 */
	public void leave(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getActiveGroupResult();
		if (groupResult == null) {
			return;
		}
		groupResult.removeActiveMember(studyResult);
		studyResult.setActiveGroupResult(null);
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
	}

}
