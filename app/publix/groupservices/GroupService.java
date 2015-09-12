package publix.groupservices;

import java.util.List;

import models.GroupModel;
import models.GroupModel.GroupState;
import models.StudyModel;
import models.StudyResult;
import persistance.GroupDao;
import persistance.StudyResultDao;
import play.Logger;
import play.db.jpa.JPA;
import publix.exceptions.ForbiddenPublixException;
import publix.services.PublixErrorMessages;

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
	private final GroupDao groupDao;

	@Inject
	GroupService(PublixErrorMessages errorMessages,
			StudyResultDao studyResultDao, GroupDao groupDao) {
		this.errorMessages = errorMessages;
		this.studyResultDao = studyResultDao;
		this.groupDao = groupDao;
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

	public boolean hasValidGroup(StudyResult studyResult) {
		GroupModel group = studyResult.getGroup();
		return group != null && group.getGroupState() != GroupState.FINISHED;
	}

	/**
	 * Joins the first incomplete Group from the DB and returns it. If such
	 * doesn't exist it creates a new one and persists it.
	 */
	public GroupModel joinGroup(StudyResult studyResult) {
		// If we already have a group just return it
		if (hasValidGroup(studyResult)) {
			return studyResult.getGroup();
		}

		// Look in the DB if we have an incomplete group. If not create new one.
		StudyModel study = studyResult.getStudy();
		GroupModel group = groupDao.findFirstIncomplete(study);
		if (group == null) {
			group = new GroupModel(study);
			groupDao.create(group);
		}

		// Add StudyResult to Group and vice versa
		group.addStudyResult(studyResult);
		studyResult.setGroup(group);

		setGroupStateInComplete(group, studyResult.getStudy());
		groupDao.update(group);
		studyResultDao.update(studyResult);
		return group;
	}

	/**
	 * Sets Group's state to COMPLETE or INCOMPLETE according to study's
	 * maxGroupSize.
	 */
	private void setGroupStateInComplete(GroupModel group, StudyModel study) {
		if (group.getStudyResultList().size() < study.getMaxGroupSize()) {
			group.setGroupState(GroupState.INCOMPLETE);
		} else {
			group.setGroupState(GroupState.COMPLETE);
		}
	}

	public GroupState getGroupState(long groupId) {
		try {
			return JPA.withTransaction(() -> {
				GroupModel group = groupDao.findById(groupId);
				return (group != null) ? group.getGroupState()
						: null;
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupState: ", e);
		}
		return null;
	}

	public List<StudyResult> getGroupStudyResultList(long groupId) {
		try {
			return JPA.withTransaction(() -> {
				GroupModel group = groupDao.findById(groupId);
				return (group != null) ? group.getStudyResultList()
						: null;
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupState: ", e);
		}
		return null;
	}

	public GroupModel getGroup(long groupId) {
		try {
			return JPA.withTransaction(() -> {
				return groupDao.findById(groupId);
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupState: ", e);
		}
		return null;
	}

	public void dropGroup(StudyResult studyResult) {
		GroupModel group = studyResult.getGroup();
		if (group == null) {
			return;
		}

		// Remove StudyResult from Group and vice versa
		group.removeStudyResult(studyResult);
		studyResult.setGroup(null);

		setGroupStateInComplete(group, studyResult.getStudy());
		groupDao.update(group);
		studyResultDao.update(studyResult);

		// If group empty remove it from DB
		if (group.getStudyResultList().isEmpty()) {
			groupDao.remove(group);
		}
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
	}

}
