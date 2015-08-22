package publix.controllers.personal_single;

import models.ComponentModel;
import models.StudyModel;
import models.workers.PersonalSingleWorker;
import persistance.ComponentResultDao;
import persistance.GroupResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.mvc.Result;
import publix.controllers.IPublix;
import publix.controllers.Publix;
import publix.controllers.StudyAssets;
import publix.exceptions.PublixException;
import publix.services.ChannelService;
import publix.services.personal_single.PersonalSingleErrorMessages;
import publix.services.personal_single.PersonalSinglePublixUtils;
import publix.services.personal_single.PersonalSingleStudyAuthorisation;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

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
	PersonalSinglePublix(PersonalSinglePublixUtils publixUtils,
			PersonalSingleStudyAuthorisation studyAuthorisation,
			PersonalSingleErrorMessages errorMessages, StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao,
			ChannelService<PersonalSingleWorker> channelService) {
		super(publixUtils, studyAuthorisation, errorMessages, studyAssets,
				componentResultDao, jsonUtils, studyResultDao, groupResultDao,
				channelService);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerIdStr = getQueryString(PERSONALSINGLE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ PERSONALSINGLE_WORKER_ID + " " + workerIdStr);
		StudyModel study = publixUtils.retrieveStudy(studyId);

		PersonalSingleWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerIdStr);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(publix.controllers.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
