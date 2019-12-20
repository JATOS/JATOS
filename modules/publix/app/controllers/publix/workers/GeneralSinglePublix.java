package controllers.publix.workers;

import controllers.publix.GeneralSingleGroupChannel;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralSingleCookieService;
import services.publix.workers.GeneralSingleErrorMessages;
import services.publix.workers.GeneralSinglePublixUtils;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import utils.common.HttpUtils;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for general single study runs (open to
 * everyone). A general single run is done by a GeneralSingleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublix extends Publix<GeneralSingleWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(GeneralSinglePublix.class);

    public static final String GENERALSINGLE = "generalSingle";

    private final GeneralSinglePublixUtils publixUtils;
    private final GeneralSingleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final WorkerCreator workerCreator;
    private final GeneralSingleCookieService generalSingleCookieService;
    private final StudyLogger studyLogger;

    @Inject
    GeneralSinglePublix(JPAApi jpa, GeneralSinglePublixUtils publixUtils,
            GeneralSingleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, WorkerCreator workerCreator,
            GeneralSingleGroupChannel groupChannel,
            IdCookieService idCookieService,
            GeneralSingleCookieService generalSingleCookieService,
            GeneralSingleErrorMessages errorMessages, StudyAssets studyAssets,
            JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel,
                idCookieService, errorMessages, studyAssets,
                jsonUtils, componentResultDao, studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.workerCreator = workerCreator;
        this.generalSingleCookieService = generalSingleCookieService;
        this.studyLogger = studyLogger;
    }

    /**
     * {@inheritDoc}<br>
     * <br>
     * <p>
     * Only a general single run or a personal single run has the special
     * StudyState PRE. Only with the corresponding workers (GeneralSingleWorker
     * and PersonalSingleWorker) it's possible to have a preview of the study.
     * To get into the preview mode one has to add 'pre' to the URL query
     * string. In the preview mode a worker can start the study (with 'pre') and
     * start the first active component as often as he wants. The study result switches
     * into 'STARTED' and back to normal behavior by starting the study without
     * the 'pre' in the query string or by going on and start a component
     * different then the first.
     */
    @Override
    public Result startStudy(Long studyId, Long batchId) throws PublixException {
        boolean pre = HttpUtils.getQueryString("pre") != null;
        LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
                + batchId + ", " + "pre " + pre);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
        Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);

        // There are 4 possibilities
        // 1. Preview study, first call -> create Worker and StudyResult, call finishOldestStudyResult, write General Single Cookie
        // 2. Preview study, second+ call (same browser) -> get StudyResult, do not call finishOldestStudyResult, do not write General Single Cookie
        // 3. No preview study, first call -> create StudyResult, call finishOldestStudyResult, write General Single Cookie
        // 4. No preview study, second+ call -> throw exception
        // Different browser always leads to a new study run
        StudyResult studyResult;
        GeneralSingleWorker worker;
        if (workerId == null) {
            worker = workerCreator.createAndPersistGeneralSingleWorker(batch);
            studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
            LOGGER.info(".startStudy: study (study ID " + studyId + ", batch ID "
                    + batchId + ") " + "assigned to worker with ID "
                    + worker.getId() + ", " + "pre " + pre);
            publixUtils.finishOldestStudyResult();
            studyResult = resultCreator.createStudyResult(study, batch, worker, pre);
            generalSingleCookieService.set(study, worker);
            studyLogger.log(study, "Started study run with " + GeneralSingleWorker.UI_WORKER_TYPE
                    + " worker", batch, worker);
        } else {
            worker = publixUtils.retrieveTypedWorker(workerId);
            studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
            studyResult = worker.getLastStudyResult()
                    .orElseThrow(() -> new InternalServerErrorPublixException(
                            "Repeated study run but couldn't find last study result"));
            if (!idCookieService.hasIdCookie(studyResult.getId())) {
                publixUtils.finishOldestStudyResult();
                generalSingleCookieService.set(study, worker);
            }
        }
        idCookieService.writeIdCookie(worker, batch, studyResult);
        publixUtils.setUrlQueryParameter(studyResult);

        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyId, firstComponent.getId(), studyResult.getId(), null));
    }

}
