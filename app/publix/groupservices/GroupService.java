package publix.groupservices;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.GroupResult;
import models.GroupResult.GroupState;
import models.StudyModel;
import models.StudyResult;
import persistance.GroupResultDao;
import persistance.StudyResultDao;
import play.db.jpa.JPA;
import publix.exceptions.ForbiddenPublixException;
import publix.services.PublixErrorMessages;

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
	 * Checks whether this StudyResult has a GroupResult that is not in state
	 * FINISHED.
	 */
	public boolean hasUnfinishedGroupResult(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getGroupResult();
		return groupResult != null
				&& groupResult.getGroupState() != GroupState.FINISHED;
	}

	/**
	 * Joins the first GroupResult where the max number of members is not
	 * reached yet and returns it. If such doesn't exist it creates a new one
	 * and persists it.
	 */
	public GroupResult joinGroup(StudyResult studyResult) {
		// If we already have a GroupResult just return it
		if (hasUnfinishedGroupResult(studyResult)) {
			return studyResult.getGroupResult();
		}

		// Look in the DB if we have an incomplete GroupResult. If not create
		// new one.
		StudyModel study = studyResult.getStudy();
		GroupResult groupResult = groupResultDao.findFirstMaxNotReached(study);
		if (groupResult == null) {
			groupResult = new GroupResult(study);
			groupResultDao.create(groupResult);
		}

		// Add StudyResult to GroupResult and vice versa
		groupResult.addStudyResult(studyResult);
		studyResult.setGroupResult(groupResult);

		groupResultDao.update(groupResult);
		studyResultDao.update(studyResult);
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
		return groupResult;
	}

	public void leaveGroupResult(StudyResult studyResult) {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			return;
		}

		// Remove StudyResult from GroupResult and vice versa
//		groupResult.removeStudyResult(studyResult);
//		studyResult.setGroupResult(null);

//		groupResultDao.update(groupResult);
//		studyResultDao.update(studyResult);

		// If GroupResult has no more members empty remove it from DB
//		if (groupResult.getStudyResultList().isEmpty()) {
//			groupResultDao.remove(groupResult);
//		}
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
	}

}
