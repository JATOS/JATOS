package publix.groupservices;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.GroupModel;
import models.GroupModel.GroupState;
import models.StudyModel;
import models.StudyResult;
import persistance.GroupDao;
import persistance.StudyResultDao;
import play.db.jpa.JPA;
import publix.exceptions.ForbiddenPublixException;
import publix.services.PublixErrorMessages;

/**
 * Handles groups, e.g. joining or leaving a group. Members of a group are
 * StudyResults, which represents a particular study run. A group is persisted
 * in a GroupModel.
 * 
 * All group members exchange messages via WebSockets that are called group
 * channels in JATOS. The message dispatching system is implemented with Akka.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class GroupService {

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

	/**
	 * Checks whether this StudyResult has a group that is not in state
	 * FINISHED.
	 */
	public boolean hasUnfinishedGroup(StudyResult studyResult) {
		GroupModel group = studyResult.getGroup();
		return group != null && group.getGroupState() != GroupState.FINISHED;
	}

	/**
	 * Joins the first group where the max number of members is not reached yet
	 * and returns it. If such doesn't exist it creates a new one and persists
	 * it.
	 */
	public GroupModel joinGroup(StudyResult studyResult) {
		// If we already have a group just return it
		if (hasUnfinishedGroup(studyResult)) {
			return studyResult.getGroup();
		}

		// Look in the DB if we have an incomplete group. If not create new one.
		StudyModel study = studyResult.getStudy();
		GroupModel group = groupDao.findFirstMaxNotReached(study);
		if (group == null) {
			group = new GroupModel(study);
			groupDao.create(group);
		}

		// Add StudyResult to Group and vice versa
		group.addStudyResult(studyResult);
		studyResult.setGroup(group);

		groupDao.update(group);
		studyResultDao.update(studyResult);
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
		return group;
	}

	public void leaveGroup(StudyResult studyResult) {
		GroupModel group = studyResult.getGroup();
		if (group == null) {
			return;
		}

		// Remove StudyResult from Group and vice versa
		group.removeStudyResult(studyResult);
		studyResult.setGroup(null);

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
