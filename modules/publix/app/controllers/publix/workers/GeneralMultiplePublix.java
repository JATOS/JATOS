package controllers.publix.workers;

import controllers.publix.GeneralMultipleGroupChannel;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralMultipleStudyAuthorisation;
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

    private final PublixUtils publixUtils;
    private final GeneralMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final WorkerCreator workerCreator;
    private final StudyLogger studyLogger;

    @Inject
    GeneralMultiplePublix(JPAApi jpa, PublixUtils publixUtils,
            GeneralMultipleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, WorkerCreator workerCreator,
            GeneralMultipleGroupChannel groupChannel,
            IdCookieService idCookieService,
            PublixErrorMessages errorMessages,
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
    public Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        GeneralMultipleWorker worker = workerCreator.createAndPersistGeneralMultipleWorker(batch);
        studyAuthorisation.checkWorkerAllowedToStartStudy(request.session(), worker, study, batch);

        publixUtils.finishOldestStudyResult();
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
        publixUtils.setUrlQueryParameter(request, studyResult);
        idCookieService.writeIdCookie(studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyLinkId " + studyLink.getId() + ", "
                + "studyResultId" + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId());
        studyLogger.log(studyLink, "Started study run with " + PersonalMultipleWorker.UI_WORKER_TYPE
                + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), firstComponent.getUuid(), null));
    }

}
