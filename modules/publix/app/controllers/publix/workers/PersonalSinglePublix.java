package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Component;
import models.common.Study;
import models.common.workers.PersonalSingleWorker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ChannelService;
import services.publix.GroupService;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import utils.common.JsonUtils;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;

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

	public static final String PERSONALSINGLE_WORKER_ID = "personalSingleWorkerId";

	private static final String CLASS_NAME = PersonalSinglePublix.class
			.getSimpleName();

	private final PersonalSinglePublixUtils publixUtils;
	private final PersonalSingleStudyAuthorisation studyAuthorisation;

	@Inject
	PersonalSinglePublix(JPAApi jpa, PersonalSinglePublixUtils publixUtils,
			PersonalSingleStudyAuthorisation studyAuthorisation,
			GroupService groupService,
			ChannelService channelService,
			PersonalSingleErrorMessages errorMessages, StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, errorMessages, studyAssets, componentResultDao,
				jsonUtils, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerIdStr = getQueryString(PERSONALSINGLE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ PERSONALSINGLE_WORKER_ID + " " + workerIdStr);
		Study study = publixUtils.retrieveStudy(studyId);

		PersonalSingleWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerIdStr);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
