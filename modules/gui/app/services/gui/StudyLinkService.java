package services.gui;

import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import models.common.workers.WorkerType;
import exceptions.common.BadRequestException;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
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

    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;

    @Inject
    StudyLinkService(BatchDao batchDao,
                     WorkerDao workerDao,
                     StudyLinkDao studyLinkDao,
                     WorkerService workerService) {
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
    }

    public List<String> getStudyCodes(Batch batch, StudyCodeProperties props) {
        if (props.getType() == null) throw new BadRequestException("Unknown type");

        return switch (props.getType()) {
            case PERSONAL_SINGLE, PERSONAL_MULTIPLE ->
                    createAndPersistStudyLinks(props.getComment(), props.getAmount(), batch, props.getType());
            case GENERAL_SINGLE, GENERAL_MULTIPLE, MT -> {
                StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, props.getType())
                        .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, props.getType())));
                yield Collections.singletonList(studyLink.getStudyCode());
            }
            default -> throw new BadRequestException("Unknown type");
        };
    }

    /**
     * Creates and persists study links for the PERSONAL worker types. It can create multiple study codes if the amount
     * is greater than 1. If the amount is smaller than 1, it returns a single study code.
     */
    private List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, WorkerType workerType) {
        return studyLinkDao.withTransaction(_ -> {
            int i = Math.max(amount, 1);

            List<String> studyCodeList = new ArrayList<>();
            while (i > 0) {
                Worker worker = switch (workerType) {
                    case PERSONAL_SINGLE -> new PersonalSingleWorker(comment);
                    case PERSONAL_MULTIPLE -> new PersonalMultipleWorker(comment);
                    default -> throw new BadRequestException("Unknown worker type");
                };
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
