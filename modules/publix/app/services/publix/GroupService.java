package services.publix;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.NoContentPublixException;
import models.common.Batch;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

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

	private final ChannelService channelService;
	private final ResultCreator resultCreator;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;
	private final JPAApi jpa;
	private final PublixErrorMessages errorMessages;

	@Inject
	GroupService(ChannelService channelService, ResultCreator resultCreator,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao,
			JPAApi jpa, PublixErrorMessages errorMessages) {
		this.channelService = channelService;
		this.resultCreator = resultCreator;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
		this.jpa = jpa;
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
	 * Persists changes in it's own transaction.
	 * 
	 * Looks in the database whether we have other incomplete GroupResult (state
	 * STARTED, maxActiveMember not reached, maxTotalMembers not reached). If
	 * there are more than one, it returns the one with the most active members.
	 * If there is no other GroupResult it throws a NoContentPublixException.
	 */
	public GroupResult reassign(StudyResult studyResult, Batch batch)
			throws NoContentPublixException, ForbiddenPublixException {
		GroupResult currentGroupResult = studyResult.getActiveGroupResult();
		if (currentGroupResult == null) {
			throw new ForbiddenPublixException(errorMessages
					.groupStudyResultNotMember(studyResult.getId()));
		}
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
		jpa.withTransaction(() -> {
			groupResultDao.update(currentGroupResult);
			groupResultDao.update(differentGroupResult);
			studyResultDao.update(studyResult);
		});
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
		jpa.withTransaction(() -> {
			groupResultDao.update(groupResult);
			studyResultDao.update(studyResult);
		});
	}

	/**
	 * Moves the given StudyResult in its group into history and closes the
	 * group channel which includes sending a left message to all group members.
	 */
	public void finishStudyInGroup(Study study, StudyResult studyResult)
			throws InternalServerErrorPublixException {
		GroupResult groupResult = studyResult.getActiveGroupResult();
		if (study.isGroupStudy() && groupResult != null) {
			moveToHistory(studyResult);
			channelService.closeGroupChannel(studyResult, groupResult);
			channelService.sendLeftMsg(studyResult, groupResult);
		}
	}

	/**
	 * Moves the given StudyResult in its group to the history member list. This
	 * should happen when a study run is done (StudyResult's state is in
	 * FINISHED, FAILED, ABORTED).
	 */
	private void moveToHistory(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getActiveGroupResult();
		groupResult.removeActiveMember(studyResult);
		groupResult.addHistoryMember(studyResult);
		studyResult.setActiveGroupResult(null);
		studyResult.setHistoryGroupResult(groupResult);
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
	}

	/**
	 * Moves all StudyResults of the given worker and the given study in their
	 * groups to the history member list, if this study is a group study and the
	 * study run is not done yet (StudyResult's state is in FINISHED, FAILED,
	 * ABORTED). E.g. this is necessary for a JatosWorker if he starts the same
	 * study a second time without actually finishing the prior study run.
	 * 
	 * It should be max one StudyResult to be treated in this way since we call
	 * this method during start of each study run, but we iterate over all
	 * StudyResults of this worker just in case.
	 */
	public void finishStudyInAllPriorGroups(Worker worker, Study study)
			throws InternalServerErrorPublixException {
		List<StudyResult> studyResultList = worker.getStudyResultList();
		for (StudyResult studyResult : studyResultList) {
			if (study.getId().equals(studyResult.getStudy().getId())
					&& !PublixHelpers.studyDone(studyResult)
					&& study.isGroupStudy()) {
				// Should be max. one StudyResult
				finishStudyInGroup(study, studyResult);
			}
		}
	}

}
