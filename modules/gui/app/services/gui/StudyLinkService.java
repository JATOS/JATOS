package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.workers.*;
import scala.Option;
import utils.common.Helpers;
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
    private final StudyService studyService;
    private final Checker checker;

    @Inject
    StudyLinkService(BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao,
            WorkerService workerService, StudyService studyService, Checker checker) {
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
        this.studyService = studyService;
        this.checker = checker;
    }

    public JsonNode getStudyCodes(String id, Option<Long> batchId, String workerType, String comment,
            Integer amount) throws ForbiddenException, NotFoundException, BadRequestException {
        Study study = studyService.getStudyFromIdOrUuid(id);

        Batch batch;
        if (batchId.nonEmpty()) {
            batch = batchDao.findById(batchId.get());
            checker.checkStandardForBatch(batch, batch.getStudy(), batchId.get());
        } else {
            batch = study.getDefaultBatch();
        }

        workerType = workerService.extractWorkerType(workerType);
        switch (workerType) {
            case PersonalSingleWorker.WORKER_TYPE:
            case PersonalMultipleWorker.WORKER_TYPE:
                return getPersonalRun(batch, workerType, comment, amount);
            case GeneralSingleWorker.WORKER_TYPE:
            case GeneralMultipleWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return getGeneralRun(batch, workerType);
            default:
                throw new BadRequestException("Unknown worker type");
        }
    }

    /**
     * Creates either Personal Single or Personal Multiple study codes and their corresponding workers.
     */
    private JsonNode getPersonalRun(Batch batch, String workerType, String comment, Integer amount)
            throws BadRequestException {
        comment = Strings.isNullOrEmpty(comment) ? "" : Helpers.urlDecode(comment);
        amount = amount == null ? 1 : amount;
        List<String> studyCodeList;
        studyCodeList = createAndPersistStudyLinks(comment, amount, batch, workerType);

        return JsonUtils.asJsonNode(studyCodeList);
    }

    private List<String> createAndPersistStudyLinks(String comment, int amount, Batch batch, String workerType)
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
