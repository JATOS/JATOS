package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
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
import utils.common.JsonUtils;

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

    public JsonNode getStudyCodes(Batch batch, StudyCodeProperties props)
            throws ForbiddenException, NotFoundException, BadRequestException {
        switch (props.getType()) {
            case PersonalSingleWorker.WORKER_TYPE:
            case PersonalMultipleWorker.WORKER_TYPE:
                return getPersonalRun(batch, props.getType(), props.getComment(), props.getAmount());
            case GeneralSingleWorker.WORKER_TYPE:
            case GeneralMultipleWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return getGeneralRun(batch, props.getType());
            default:
                throw new BadRequestException("Unknown type");
        }
    }

    /**
     * Creates either Personal Single or Personal Multiple study codes and their corresponding workers.
     */
    private JsonNode getPersonalRun(Batch batch, String workerType, String comment, int amount)
            throws BadRequestException {
        List<String> studyCodeList = createAndPersistStudyLinks(comment, amount, batch, workerType);
        return JsonUtils.asJsonNode(studyCodeList);
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

    /**
     * Returns the study code for the given worker type (possible types are GeneralMultipleWorker, GeneralSingleWorker,
     * MTWorker). Since for the 'General' workers only one study link is necessary per batch and worker type it only
     * creates one if it is not already stored in the StudyLink table.
     */
    private JsonNode getGeneralRun(Batch batch, String workerType) {
        StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, workerType)));
        return JsonUtils.asJsonNode(Collections.singletonList(studyLink.getStudyCode()));
    }

}
