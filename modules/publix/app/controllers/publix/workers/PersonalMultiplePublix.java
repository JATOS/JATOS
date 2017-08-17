package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import group.GroupAdministration;
import group.GroupChannelService;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalMultipleErrorMessages;
import services.publix.workers.PersonalMultiplePublixUtils;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import utils.common.HttpUtils;
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
public class PersonalMultiplePublix extends Publix<PersonalMultipleWorker>
        implements IPublix {

    public static final String PERSONAL_MULTIPLE_WORKER_ID =
            "personalMultipleWorkerId";

    private static final ALogger LOGGER = Logger
            .of(PersonalMultiplePublix.class);

    private final PersonalMultiplePublixUtils publixUtils;
    private final PersonalMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;

    @Inject
    PersonalMultiplePublix(JPAApi jpa, PersonalMultiplePublixUtils publixUtils,
            PersonalMultipleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator,
            GroupAdministration groupAdministration,
            GroupChannelService groupChannelService,
            IdCookieService idCookieService,
            PersonalMultipleErrorMessages errorMessages,
            StudyAssets studyAssets, JsonUtils jsonUtils,
            ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
        super(jpa, publixUtils, studyAuthorisation,
                groupAdministration, groupChannelService, idCookieService,
                errorMessages, studyAssets, jsonUtils, componentResultDao,
                studyResultDao, groupResultDao);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
    }

    @Override
    public Result startStudy(Long studyId, Long batchId)
            throws PublixException {
        String workerIdStr = HttpUtils
                .getQueryString(PERSONAL_MULTIPLE_WORKER_ID);
        LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
                + batchId + ", " + "workerId " + workerIdStr);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
        PersonalMultipleWorker worker = publixUtils
                .retrieveTypedWorker(workerIdStr);
        studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

        publixUtils.finishAbandonedStudyResults();
        StudyResult studyResult = resultCreator.createStudyResult(study, batch,
                worker);
        idCookieService.writeIdCookie(worker, batch, studyResult);

        Component firstComponent = publixUtils
                .retrieveFirstActiveComponent(study);
        return redirect(
                controllers.publix.routes.PublixInterceptor.startComponent(
                        studyId, firstComponent.getId(), studyResult.getId()));
    }

}
