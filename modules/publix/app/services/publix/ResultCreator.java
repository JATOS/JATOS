package services.publix;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.*;
import models.common.workers.Worker;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class that creates ComponentResults and StudyResults and GroupResults
 *
 * @author Kristian Lange (2016)
 */
@Singleton
public class ResultCreator {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final WorkerDao workerDao;

    @Inject
    ResultCreator(ComponentResultDao componentResultDao, StudyResultDao studyResultDao, WorkerDao workerDao) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.workerDao = workerDao;
    }

    /**
     * Creates StudyResult and adds it to the given Worker.
     */
    public StudyResult createStudyResult(Study study, Batch batch, Worker worker, boolean pre) {
        StudyResult studyResult = new StudyResult(study, batch, worker);
        if (pre) {
            studyResult.setStudyState(StudyResult.StudyState.PRE);
        } else {
            studyResult.setStudyState(StudyResult.StudyState.STARTED);
        }
        worker.addStudyResult(studyResult);
        studyResultDao.create(studyResult);
        workerDao.update(worker);
        return studyResult;
    }

    /**
     * Creates StudyResult and adds it to the given Worker.
     */
    public StudyResult createStudyResult(Study study, Batch batch, Worker worker) {
        return createStudyResult(study, batch, worker, false);
    }

    public ComponentResult createComponentResult(StudyResult studyResult, Component component) {
        ComponentResult componentResult = new ComponentResult(component);
        componentResult.setStudyResult(studyResult);
        studyResult.addComponentResult(componentResult);
        componentResultDao.create(componentResult);
        studyResultDao.update(studyResult);
        return componentResult;
    }

}
