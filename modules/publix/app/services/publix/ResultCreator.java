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
 */
@Singleton
public class ResultCreator {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final WorkerDao workerDao;

    @Inject
    ResultCreator(ComponentResultDao componentResultDao,
                  StudyResultDao studyResultDao,
                  WorkerDao workerDao) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.workerDao = workerDao;
    }

    /**
     * Creates StudyResult and adds it to the given Worker.
     */
    public StudyResult createStudyResult(StudyLink studyLink, Worker worker) {
        return studyResultDao.withTransaction(em -> {
            StudyResult studyResult = new StudyResult(studyLink, worker);
            if (studyResult.getStudy().isAllowPreview() && PublixHelpers.isPreviewWorker(worker)) {
                studyResult.setStudyState(StudyResult.StudyState.PRE);
            } else {
                studyResult.setStudyState(StudyResult.StudyState.STARTED);
            }
            worker.addStudyResult(studyResult);
            studyResultDao.persist(studyResult);
            workerDao.merge(worker);
            return studyResult;
        });
    }

    public ComponentResult createComponentResult(StudyResult studyResult, Component component) {
        return studyResultDao.withTransaction(em -> {
            ComponentResult componentResult = new ComponentResult(component);
            componentResult.setStudyResult(studyResult);
            studyResult.addComponentResult(componentResult);
            componentResultDao.persist(componentResult);
            studyResultDao.merge(studyResult);
            return componentResult;
        });
    }

}
