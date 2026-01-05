package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import filters.publix.IdCookieFilter;
import filters.publix.IdCookieFilter.IdCookies;
import general.common.IOExecutor;
import general.common.StudyAssetsExecutor;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.*;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import actions.common.TransactionalAction.Transactional;

import javax.inject.Inject;
import javax.inject.Singleton;

import static play.mvc.Results.redirect;

/**
 * Implementation of JATOS' public API for studies run by PersonalMultipleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublix extends Publix implements IPublix {

    private static final ALogger LOGGER = Logger.of(PersonalMultiplePublix.class);

    private final PublixUtils publixUtils;
    private final PersonalMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    PersonalMultiplePublix(JPAApi jpa, PublixUtils publixUtils,
                           PersonalMultipleStudyAuthorisation studyAuthorisation,
                           ResultCreator resultCreator,
                           GroupAdministration groupAdministration,
                           IdCookieService idCookieService,
                           PublixErrorMessages errorMessages,
                           StudyAssets studyAssets, JsonUtils jsonUtils,
                           ComponentResultDao componentResultDao,
                           StudyResultDao studyResultDao,
                           StudyLogger studyLogger,
                           IOUtils ioUtils,
                           IOExecutor dbContext,
                           StudyAssetsExecutor studyAssetsExecutor) {
        super(jpa, publixUtils, studyAuthorisation, groupAdministration, idCookieService, errorMessages, studyAssets,
                jsonUtils, componentResultDao, studyResultDao, studyLogger, ioUtils, dbContext, studyAssetsExecutor);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.studyLogger = studyLogger;
    }

    @Override
    @IdCookies
    public Result startStudy(Http.Request request, StudyLink studyLink) {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        PersonalMultipleWorker worker = (PersonalMultipleWorker) studyLink.getWorker();
        studyAuthorisation.checkWorkerAllowedToStartStudy(request.session(), worker, study, batch);

        publixUtils.finishOldestStudyResult(request);
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
        publixUtils.setUrlQueryParameter(request, studyResult);
        idCookieService.writeIdCookie(request, studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyCode " + studyLink.getStudyCode() + ", "
                + "studyResultId " + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId());
        studyLogger.log(studyLink, "Started study run with " + PersonalMultipleWorker.UI_WORKER_TYPE
                + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), firstComponent.getUuid(), null));
    }

}
