package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.PersonalSingleGroupChannel;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.PersonalSingleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Implementation of JATOS' public API for personal single study runs. A personal single run is done by a
 * PersonalSingleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublix extends Publix<PersonalSingleWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(PersonalSinglePublix.class);

    private final PublixUtils publixUtils;
    private final PersonalSingleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    PersonalSinglePublix(JPAApi jpa, PublixUtils publixUtils,
            PersonalSingleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, PersonalSingleGroupChannel groupChannel,
            IdCookieService idCookieService,
            PublixErrorMessages errorMessages, StudyAssets studyAssets,
            JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel,
                idCookieService, errorMessages, studyAssets, jsonUtils,
                componentResultDao, studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.studyLogger = studyLogger;
    }

    /**
     * {@inheritDoc}
     *
     * Only a general single run or a personal single run has the special
     * StudyState PRE. Only with the corresponding workers (GeneralSingleWorker
     * and PersonalSingleWorker) it's possible to have a preview of the study.
     * To get into the preview mode the Study's 'allowPreview' flag has to be set.
     * In the preview mode a worker can start the first active component as often
     * as they want. As soon as the worker goes on and starts another component
     * the study result switches into 'STARTED' and back to normal behavior.
     */
    @Override
    public Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        PersonalSingleWorker worker = (PersonalSingleWorker) studyLink.getWorker();
        studyAuthorisation.checkWorkerAllowedToStartStudy(request.session(), worker, study, batch);

        // There are 5 possibilities
        // 1. Preview study, first call -> create StudyResult, call finishOldestStudyResult
        // 2. Preview study, second+ call, same browser -> get StudyResult, do not call finishOldestStudyResult
        // 3. Preview study, second+ call, different browser -> get StudyResult, call finishOldestStudyResult
        // 4. No preview study, first call -> create StudyResult, call finishOldestStudyResult
        // 5. No preview study, second+ call -> throw exception
        Optional<StudyResult> studyResultOpt = worker.getLastStudyResult();
        StudyResult studyResult;
        if (!studyResultOpt.isPresent()) {
            publixUtils.finishOldestStudyResult();
            studyResult = resultCreator.createStudyResult(studyLink, worker);
        } else {
            if (!idCookieService.hasIdCookie(studyResultOpt.get().getId())) {
                publixUtils.finishOldestStudyResult();
            }
            studyResult = studyResultOpt.get();
        }
        idCookieService.writeIdCookie(studyResult);
        publixUtils.setUrlQueryParameter(request, studyResult);
        Component component = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyCode " + studyLink.getStudyCode() + ", "
                + "studyResultId" + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId() + ", "
                + "preview " + study.isAllowPreview());
        studyLogger.log(studyLink, "Started study run with " + PersonalSingleWorker.UI_WORKER_TYPE
                + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), component.getUuid(), null));
    }

}
