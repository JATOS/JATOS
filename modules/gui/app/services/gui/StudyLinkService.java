package services.gui;

import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyLinkService {

    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;

    @Inject
    StudyLinkService(BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao, WorkerService workerService) {
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
    }

    public List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, String workerType)
            throws BadRequestException {
        amount = Math.max(amount, 1);

        List<String> studyLinkIdList = new ArrayList<>();
        while (amount > 0) {
            Worker worker;
            switch (workerType) {
                case PersonalSingleWorker.WORKER_TYPE:
                    worker = new PersonalSingleWorker(comment);
                    break;
                case PersonalMultipleWorker.WORKER_TYPE:
                    worker = new PersonalMultipleWorker(comment);
                    break;
                default:
                    throw new BadRequestException("Unknown worker type");
            }
            workerService.validateWorker(worker);
            batch.addWorker(worker);
            workerDao.create(worker);

            StudyLink studyLink = new StudyLink(batch, worker);
            studyLinkDao.create(studyLink);
            studyLinkIdList.add(studyLink.getId());

            batchDao.update(batch);
            amount--;
        }
        return studyLinkIdList;
    }

}
