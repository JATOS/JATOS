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
import play.Logger;
import play.db.jpa.JPAApi;

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

    private static final Logger.ALogger LOGGER = Logger.of(StudyLinkService.class);

    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;
    private final JPAApi jpa;

    @Inject
    StudyLinkService(BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao, WorkerService workerService,
            JPAApi jpa) {
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
        this.jpa = jpa;
    }

    public List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, String workerType)
            throws BadRequestException {
        amount = Math.max(amount, 1);

        List<String> studyCodeList = new ArrayList<>();
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
            studyCodeList.add(studyLink.getStudyCode());

            batchDao.update(batch);
            amount--;
        }
        return studyCodeList;
    }

    /**
     * This method is only used during update from version <3.7.1. It creates for each existing
     * PersonalSingleWorker and PersonalMultipleWorker a StudyLink.
     */
    public void createStudyLinksForExistingPersonalWorkers() {
        jpa.withTransaction(() -> {
            if (studyLinkDao.countAll() != 0) return;

            int studyLinkCounter = 0;
            List<Worker> allWorkers = workerDao.findAll();
            for (Worker worker : allWorkers) {
                if (worker.getWorkerType().equals(PersonalSingleWorker.WORKER_TYPE) ||
                        worker.getWorkerType().equals(PersonalMultipleWorker.WORKER_TYPE)) {
                    for (Batch batch : worker.getBatchList()) {
                        if (studyLinkDao.findByBatchAndWorker(batch, worker).isPresent()) continue;
                        StudyLink studyLink = new StudyLink(batch, worker);
                        studyLinkDao.create(studyLink);
                        studyLinkCounter++;
                        LOGGER.info("Created study link " + studyLinkCounter);
                    }
                }
            }
            if (studyLinkCounter > 0) LOGGER.info("Created " + studyLinkCounter + " study links for existing workers");
        });
    }

}
