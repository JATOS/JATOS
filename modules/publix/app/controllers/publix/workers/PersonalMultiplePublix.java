package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import general.common.StudyLogger;
import group.GroupAdministration;
import json.common.DomainJsonMapper;
import models.common.*;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.WorkerType;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import static play.mvc.Results.redirect;

/**
 * Implementation of JATOS' public API for studies run by PersonalMultipleWorker.
 */
@Singleton
public class PersonalMultiplePublix extends Publix implements IPublix {

    private static final ALogger LOGGER = Logger.of(PersonalMultiplePublix.class);

    private final PublixUtils publixUtils;
    private final PersonalMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    PersonalMultiplePublix(PublixUtils publixUtils,
                           PersonalMultipleStudyAuthorisation studyAuthorisation,
                           ResultCreator resultCreator,
                           GroupAdministration groupAdministration,
                           IdCookieService idCookieService,
                           PublixErrorMessages errorMessages,
                           StudyAssets studyAssets, DomainJsonMapper domainJsonMapper,
                           ComponentResultDao componentResultDao,
                           StudyResultDao studyResultDao,
                           StudyLogger studyLogger,
                           IOUtils ioUtils,
                           IOExecutor ioContext,
                           StudyAssetsExecutor studyAssetsExecutor) {
        super(publixUtils, studyAuthorisation, groupAdministration, idCookieService, errorMessages, studyAssets,
                domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils, ioContext, studyAssetsExecutor);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.studyLogger = studyLogger;
    }

    @Override
    public Result startStudy(Http.Request request, StudyLink studyLink) {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        PersonalMultipleWorker worker = (PersonalMultipleWorker) studyLink.getWorker();
        studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

        publixUtils.finishOldestStudyRun();
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
        publixUtils.setUrlQueryParameter(studyResult);
        idCookieService.writeIdCookie(studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyCode " + studyLink.getStudyCode() + ", "
                + "studyResultId " + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId());
        studyLogger.log(studyLink, "Started study run with " + WorkerType.PERSONAL_MULTIPLE + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), firstComponent.getUuid(), null));
    }

}
