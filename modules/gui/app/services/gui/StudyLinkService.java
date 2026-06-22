package services.gui;

import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;
import models.gui.StudyCodeProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service class for everything related to StudyLinks.
 */
@Singleton
public class StudyLinkService {

    private final JPAApi jpa;
    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;

    @Inject
    StudyLinkService(JPAApi jpa,
                     BatchDao batchDao,
                     WorkerDao workerDao,
                     StudyLinkDao studyLinkDao,
                     WorkerService workerService) {
        this.jpa = jpa;
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
    }

    public List<String> getStudyCodes(Batch batch, StudyCodeProperties props) {
        switch (props.getType()) {
            case PERSONAL_SINGLE:
            case PERSONAL_MULTIPLE:
                return createAndPersistStudyLinks(props.getComment(), props.getAmount(), batch, props.getType());
            case GENERAL_SINGLE:
            case GENERAL_MULTIPLE:
            case MT:
                StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, props.getType())
                        .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, props.getType())));
                return Collections.singletonList(studyLink.getStudyCode());
            default:
                throw new BadRequestException("Unknown type");
        }
    }

    private List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, WorkerType workerType) {
        return jpa.withTransaction(em -> {
            int i = Math.max(amount, 1);

            List<String> studyCodeList = new ArrayList<>();
            while (i > 0) {
                Worker worker;
                switch (workerType) {
                    case PERSONAL_SINGLE:
                        worker = new PersonalSingleWorker(comment);
                        break;
                    case PERSONAL_MULTIPLE:
                        worker = new PersonalMultipleWorker(comment);
                        break;
                    default:
                        throw new BadRequestException("Unknown worker type");
                }
                workerService.validateWorker(worker);
                workerDao.persist(worker);
                batchDao.addWorkerToBatch(batch.getId(), worker.getId());

                StudyLink studyLink = new StudyLink(batch, worker);
                studyLinkDao.persist(studyLink);
                studyCodeList.add(studyLink.getStudyCode());
                i--;
            }
            return studyCodeList;
        });
    }

}
