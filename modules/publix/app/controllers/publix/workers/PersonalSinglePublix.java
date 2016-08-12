package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
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
import services.publix.group.ChannelService;
import services.publix.group.GroupService;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import utils.common.JsonUtils;

/**
 * Implementation of JATOS' public API for personal single study runs (runs with
 * invitation and pre-created worker). A personal single run is done by a
 * PersonalSingleWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublix extends Publix<PersonalSingleWorker>
		implements IPublix {

	public static final String PERSONAL_SINGLE_WORKER_ID = "personalSingleWorkerId";

	private static final ALogger LOGGER = Logger.of(PersonalSinglePublix.class);

	private final PersonalSinglePublixUtils publixUtils;
	private final PersonalSingleStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;

	@Inject
	PersonalSinglePublix(JPAApi jpa, PersonalSinglePublixUtils publixUtils,
			PersonalSingleStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, GroupService groupService,
			ChannelService channelService, IdCookieService idCookieService,
			PersonalSingleErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, idCookieService, errorMessages, studyAssets,
				jsonUtils, componentResultDao, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		String workerIdStr = getQueryString(PERSONAL_SINGLE_WORKER_ID);
		LOGGER.info(
				".startStudy: studyId " + studyId + ", " + "batchId " + batchId
						+ ", " + PERSONAL_SINGLE_WORKER_ID + " " + workerIdStr);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		PersonalSingleWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		session(STUDY_ASSETS, study.getDirName());

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
