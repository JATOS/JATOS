package services.publix;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;

/**
 * Service class that creates ComponentResults and StudyResults and GroupResults
 * 
 * @author Kristian Lange (2016)
 */
@Singleton
public class ResultCreator {

	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;
	private final WorkerDao workerDao;

	@Inject
	ResultCreator(ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao,
			WorkerDao workerDao) {
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
		this.workerDao = workerDao;
	}

	/**
	 * Creates StudyResult and adds it to the given Worker.
	 */
	public StudyResult createStudyResult(Study study, Batch batch,
			Worker worker) {
		StudyResult studyResult = new StudyResult(study, batch);
		worker.addStudyResult(studyResult);
		studyResultDao.create(studyResult);
		workerDao.update(worker);
		return studyResult;
	}

	public ComponentResult createComponentResult(StudyResult studyResult,
			Component component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.setStudyResult(studyResult);
		studyResult.addComponentResult(componentResult);
		componentResultDao.create(componentResult);
		studyResultDao.update(studyResult);
		return componentResult;
	}

}
