package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;
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

    private final JPAApi jpa;
    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;
    private final StudyService studyService;
    private final Checker checker;

    @Inject
    StudyLinkService(JPAApi jpa,
                     BatchDao batchDao,
                     WorkerDao workerDao,
                     StudyLinkDao studyLinkDao,
                     WorkerService workerService,
                     StudyService studyService,
                     Checker checker) {
        this.jpa = jpa;
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
        this.studyService = studyService;
        this.checker = checker;
    }

    public JsonNode getStudyCodes(String id, Option<Long> batchId, WorkerType workerType,
                                  String comment, Integer amount) {
        Study study = studyService.getStudyFromIdOrUuid(id);

        Batch batch;
        if (batchId.nonEmpty()) {
            batch = batchDao.findById(batchId.get());
            checker.checkStandardForBatch(batch, batch.getStudy(), batchId.get());
        } else {
            batch = study.getDefaultBatch();
        }

        switch (workerType) {
            case PERSONAL_SINGLE:
            case PERSONAL_MULTIPLE:
                return getPersonalRun(batch, workerType, comment, amount);
            case GENERAL_SINGLE:
            case GENERAL_MULTIPLE:
            case MT:
                return getGeneralRun(batch, workerType);
            default:
                throw new BadRequestException("Unknown worker type");
        }
    }

    /**
     * Creates either Personal Single or Personal Multiple study codes and their corresponding workers.
     */
    private JsonNode getPersonalRun(Batch batch, WorkerType workerType, String comment, Integer amount) {
        comment = Strings.isNullOrEmpty(comment) ? "" : Helpers.urlDecode(comment);
        amount = amount == null ? 1 : amount;
        List<String> studyCodeList;
        studyCodeList = createAndPersistStudyLinks(comment, amount, batch, workerType);

        return JsonUtils.asJsonNode(studyCodeList);
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
                batch.addWorker(worker);
                workerDao.persist(worker);

                StudyLink studyLink = new StudyLink(batch, worker);
                studyLinkDao.persist(studyLink);
                studyCodeList.add(studyLink.getStudyCode());

                batchDao.merge(batch);
                i--;
            }
            return studyCodeList;
        });
    }

    /**
     * Returns the study code for the given worker type (possible types are GeneralMultipleWorker, GeneralSingleWorker,
     * MTWorker). Since for the 'General' workers only one study link is necessary per batch and worker type it only
     * creates one if it is not already stored in the StudyLink table.
     */
    private JsonNode getGeneralRun(Batch batch, WorkerType workerType) {
        return jpa.withTransaction(em -> {
            StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                    .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, workerType)));
            return JsonUtils.asJsonNode(Collections.singletonList(studyLink.getStudyCode()));
        });
    }

}
