package controllers.publix.personal_multiple;

import groupservices.publix.ChannelService;
import groupservices.publix.GroupService;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Component;
import models.common.Study;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.personal_multiple.PersonalMultipleErrorMessages;
import services.publix.personal_multiple.PersonalMultiplePublixUtils;
import services.publix.personal_multiple.PersonalMultipleStudyAuthorisation;
import utils.common.JsonUtils;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for studies run by
 * PersonalMultipleWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublix extends Publix<PersonalMultipleWorker>
		implements IPublix {

	public static final String PERSONAL_MULTIPLE_ID = "personalMultipleId";

	private static final String CLASS_NAME = PersonalMultiplePublix.class
			.getSimpleName();

	private final PersonalMultiplePublixUtils publixUtils;
	private final PersonalMultipleStudyAuthorisation studyAuthorisation;

	@Inject
	PersonalMultiplePublix(JPAApi jpa, PersonalMultiplePublixUtils publixUtils,
			PersonalMultipleStudyAuthorisation studyAuthorisation,
			GroupService groupService, ChannelService channelService,
			PersonalMultipleErrorMessages errorMessages,
			StudyAssets studyAssets, ComponentResultDao componentResultDao,
			JsonUtils jsonUtils, StudyResultDao studyResultDao,
			GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, errorMessages, studyAssets, componentResultDao,
				jsonUtils, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerId = getQueryString(PERSONAL_MULTIPLE_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "workerId " + workerId);
		Study study = publixUtils.retrieveStudy(studyId);

		PersonalMultipleWorker worker = publixUtils
				.retrieveTypedWorker(workerId);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerId);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
