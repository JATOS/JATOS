package controllers.publix.personal_single;

import models.ComponentModel;
import models.StudyModel;
import models.workers.PersonalSingleWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.mvc.Result;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for personal single study runs
 * (runs with invitation and pre-created worker).
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

	@Inject
	PersonalSinglePublix(PersonalSinglePublixUtils publixUtils,
			PersonalSingleErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		super(publixUtils, errorMessages, componentResultDao, jsonUtils,
				studyResultDao);
		this.publixUtils = publixUtils;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerIdStr = getQueryString(PERSONALSINGLE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ PERSONALSINGLE_WORKER_ID + " " + workerIdStr);
		StudyModel study = publixUtils.retrieveStudy(studyId);

		PersonalSingleWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerIdStr);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
