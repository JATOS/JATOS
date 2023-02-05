package services.gui;

import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.User;
import models.common.workers.*;
import play.Logger;
import play.db.jpa.JPAApi;
import scala.Option;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class StudyLinkService {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLinkService.class);

    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;
    private final WorkerService workerService;
    private final AuthService authenticationService;
    private final Checker checker;
    private final JPAApi jpa;

    @Inject
    StudyLinkService(StudyDao studyDao, BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao, WorkerService workerService,
            AuthService authenticationService, Checker checker, JPAApi jpa) {
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
        this.workerService = workerService;
        this.authenticationService = authenticationService;
        this.checker = checker;
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

    public JsonNode getStudyCodes(String id, Option<Long> batchId, String workerType, String comment,
            Integer amount) throws ForbiddenException, NotFoundException, BadRequestException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Optional<Long> studyId = Helpers.parseLong(id);
        Study study;
        if (studyId.isPresent()) {
            study = studyDao.findById(studyId.get());
            if (study == null) throw new NotFoundException("Couldn't find study with ID " + studyId.get());
            checker.checkStandardForStudy(study, studyId.get(), loggedInUser);
        } else {
            study = studyDao.findByUuid(id)
                    .orElseThrow(() -> new NotFoundException("Couldn't find study with UUID " + id));
            checker.checkStandardForStudy(study, study.getId(), loggedInUser);
        }

        Batch batch;
        if (batchId.nonEmpty()) {
            batch = batchDao.findById(batchId.get());
        } else {
            batch = study.getDefaultBatch();
        }

        checker.checkStandardForBatch(batch, batch.getStudy(), batchId.get());
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
