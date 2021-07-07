package services.publix;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.Component;
import models.common.ComponentResult;
import models.common.StudyLink;
import models.common.StudyResult;
import models.common.workers.Worker;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class that creates ComponentResults, StudyResults, and GroupResults
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
    public StudyResult createStudyResult(StudyLink studyLink, Worker worker) {
        StudyResult studyResult = new StudyResult(studyLink, worker);
        if (studyResult.getStudy().isAllowPreview()) {
            studyResult.setStudyState(StudyResult.StudyState.PRE);
        } else {
            studyResult.setStudyState(StudyResult.StudyState.STARTED);
        }
        worker.addStudyResult(studyResult);
        studyResultDao.create(studyResult);
        workerDao.update(worker);
        return studyResult;
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
