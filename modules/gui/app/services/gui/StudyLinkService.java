package services.gui;

import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.*;
import models.gui.StudyCodeProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
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
    StudyLinkService(BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao,
            WorkerService workerService) {
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
    }

    public List<String> getStudyCodes(Batch batch, StudyCodeProperties props)
            throws ForbiddenException, NotFoundException, BadRequestException {
        switch (props.getType()) {
            case PersonalSingleWorker.WORKER_TYPE:
            case PersonalMultipleWorker.WORKER_TYPE:
                return createAndPersistStudyLinks(props.getComment(), props.getAmount(), batch, props.getType());
            case GeneralSingleWorker.WORKER_TYPE:
            case GeneralMultipleWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, props.getType())
                        .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, props.getType())));
                return Collections.singletonList(studyLink.getStudyCode());
            default:
                throw new BadRequestException("Unknown type");
        }
    }

    private List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, String workerType)
            throws BadRequestException {
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

}
