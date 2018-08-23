package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.PersonalSingleGroupChannel;
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
import models.common.workers.PersonalSingleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for personal single study runs (runs with
 * invitation and pre-created worker). A personal single run is done by a
 * PersonalSingleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublix extends Publix<PersonalSingleWorker> implements IPublix {

    public static final String PERSONAL_SINGLE_WORKER_ID = "personalSingleWorkerId";

    private static final ALogger LOGGER = Logger.of(PersonalSinglePublix.class);

    private final PersonalSinglePublixUtils publixUtils;
    private final PersonalSingleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    PersonalSinglePublix(JPAApi jpa, PersonalSinglePublixUtils publixUtils,
            PersonalSingleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, PersonalSingleGroupChannel groupChannel,
            IdCookieService idCookieService,
            PersonalSingleErrorMessages errorMessages, StudyAssets studyAssets,
            JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel,
                idCookieService, errorMessages, studyAssets, jsonUtils,
                componentResultDao, studyResultDao, studyLogger);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
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
     * start the first component as often as he wants. The study result switches
     * into 'STARTED' and back to normal behavior by starting the study without
     * the 'pre' in the query string or by going on and start a component
     * different then the first.
     */
    @Override
    public Result startStudy(Long studyId, Long batchId) throws PublixException {
        String workerIdStr = HttpUtils.getQueryString(PERSONAL_SINGLE_WORKER_ID);
        boolean pre = HttpUtils.getQueryString("pre") != null;
        LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
                + batchId + ", " + PERSONAL_SINGLE_WORKER_ID + " " + workerIdStr
                + ", " + "pre " + pre);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
        PersonalSingleWorker worker = publixUtils.retrieveTypedWorker(workerIdStr);
        studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

        // 1. pre first -> new sr, new cookie, finishAbandonedStudyResults
        // 2. pre other, same browser -> get sr, keep cookie, -
        // 3. pre other, different browser -> get sr, new cookie, finishAbandonedStudyResults
        // 4. no pre first -> new sr, new cookie, finishAbandonedStudyResults
        // 5. no pre other -> exception
        publixUtils.finishAbandonedStudyResults();
        StudyResult studyResult;
        if (worker.getStudyResultList().isEmpty()) {
            studyResult = resultCreator.createStudyResult(study, batch, worker);
        } else {
            studyResult = worker.getLastStudyResult();
        }
        idCookieService.writeIdCookie(worker, batch, studyResult);
        publixUtils.setPreStudyStateByPre(pre, studyResult);
        publixUtils.setUrlQueryParameter(studyResult);

        Component component = publixUtils.retrieveFirstActiveComponent(study);
        studyLogger.log(study, "Started study run with " + PersonalSingleWorker.UI_WORKER_TYPE
                + " worker", batch, worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyId, component.getId(), studyResult.getId()));
    }

}
