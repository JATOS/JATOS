package publix.services;

import models.GroupResult;
import models.GroupResult.GroupState;
import models.StudyModel;
import models.StudyResult;
import persistance.GroupResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.db.jpa.JPA;
import publix.exceptions.ForbiddenPublixException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Kristian Lange
 */
@Singleton
public class GroupService {

	private static final String CLASS_NAME = GroupService.class.getSimpleName();

	private final PublixErrorMessages errorMessages;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;

	@Inject
	GroupService(PublixErrorMessages errorMessages,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		this.errorMessages = errorMessages;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	/**
	 * Throws ForbiddenPublixException if study is not a group study.
	 */
	public void checkStudyIsGroupStudy(StudyModel study)
			throws ForbiddenPublixException {
		if (!study.isGroupStudy()) {
			throw new ForbiddenPublixException(
					errorMessages.studyNotGroupStudy(study.getId()));
		}
	}

	/**
	 * Joins the first incomplete GroupResult from the DB and returns it. If
	 * such doesn't exist it creates a new one and persists it.
	 */
	public GroupResult joinGroupResult(StudyResult studyResult) {
		// If we already have a group just return it
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult != null
				&& groupResult.getGroupState() != GroupState.FINISHED) {
			return studyResult.getGroupResult();
		}

		// Look in the DB if we have an incomplete group. If not create new one.
		StudyModel study = studyResult.getStudy();
		groupResult = groupResultDao.findFirstIncomplete(study);
		if (groupResult == null) {
			groupResult = new GroupResult(study);
			groupResultDao.create(groupResult);
		}

		// Add StudyResult to GroupResult and vice versa
		groupResult.addStudyResult(studyResult);
		studyResult.setGroupResult(groupResult);

		setGroupStateInComplete(groupResult, studyResult.getStudy());
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
		return groupResult;
	}

	/**
	 * Sets GroupResult's state to COMPLETE or INCOMPLETE according to study's
	 * maxGroupSize.
	 */
	private void setGroupStateInComplete(GroupResult groupResult,
			StudyModel study) {
		if (groupResult.getStudyResultList().size() < study.getMaxGroupSize()) {
			groupResult.setGroupState(GroupState.INCOMPLETE);
		} else {
			groupResult.setGroupState(GroupState.COMPLETE);
		}
	}

	public GroupState getGroupState(long groupResultId) {
		try {
			return JPA.withTransaction(() -> {
				GroupResult groupResult = groupResultDao
						.findById(groupResultId);
				return (groupResult != null) ? groupResult.getGroupState()
						: null;
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupState: ", e);
		}
		return null;
	}

	public void dropGroupResult(long studyResultId) {
		try {
			JPA.withTransaction(() -> {
				StudyResult studyResult = studyResultDao
						.findById(studyResultId);
				dropGroupResult(studyResult);
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupState: ", e);
		}
	}

	public void dropGroupResult(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			return;
		}

		// Remove StudyResult from GroupResult and vice versa
		groupResult.removeStudyResult(studyResult);
		studyResult.setGroupResult(null);

		setGroupStateInComplete(groupResult, studyResult.getStudy());
		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);

		// If group empty remove it from DB
		if (groupResult.getStudyResultList().isEmpty()) {
			groupResultDao.remove(groupResult);
		}
		
	}

}
