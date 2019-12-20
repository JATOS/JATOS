package controllers.publix.workers;

import controllers.publix.GeneralMultipleGroupChannel;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralMultiplePublixUtils;
import services.publix.workers.GeneralMultipleStudyAuthorisation;
import services.publix.workers.PersonalMultipleErrorMessages;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for studies run by
 * PersonalMultipleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralMultiplePublix extends Publix<GeneralMultipleWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(GeneralMultiplePublix.class);

    public static final String GENERALMULTIPLE = "generalMultiple";

    private final GeneralMultiplePublixUtils publixUtils;
    private final GeneralMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final WorkerCreator workerCreator;
    private final StudyLogger studyLogger;

    @Inject
    GeneralMultiplePublix(JPAApi jpa, GeneralMultiplePublixUtils publixUtils,
            GeneralMultipleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, WorkerCreator workerCreator,
            GeneralMultipleGroupChannel groupChannel,
            IdCookieService idCookieService,
            PersonalMultipleErrorMessages errorMessages,
            StudyAssets studyAssets, JsonUtils jsonUtils,
            ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation,
                groupChannel, idCookieService, errorMessages, studyAssets,
                jsonUtils, componentResultDao, studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.workerCreator = workerCreator;
        this.studyLogger = studyLogger;
    }

    @Override
    public Result startStudy(Long studyId, Long batchId) throws PublixException {
        LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId " + batchId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);

        GeneralMultipleWorker worker = workerCreator.createAndPersistGeneralMultipleWorker(batch);
        studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
        LOGGER.info(".startStudy: study (study ID " + studyId + ", batch ID "
                + batchId + ") " + "assigned to worker with ID " + worker.getId());
        publixUtils.finishOldestStudyResult();
        StudyResult studyResult = resultCreator.createStudyResult(study, batch, worker);
        publixUtils.setUrlQueryParameter(studyResult);
        idCookieService.writeIdCookie(worker, batch, studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);
        studyLogger.log(study, "Started study run with " + PersonalMultipleWorker.UI_WORKER_TYPE
                + " worker", batch, worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyId, firstComponent.getId(), studyResult.getId(), null));
    }

}
