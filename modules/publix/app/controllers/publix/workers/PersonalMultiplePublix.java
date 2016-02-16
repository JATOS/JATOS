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
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ChannelService;
import services.publix.GroupService;
import services.publix.ResultCreator;
import services.publix.workers.PersonalMultipleErrorMessages;
import services.publix.workers.PersonalMultiplePublixUtils;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import utils.common.JsonUtils;

/**
 * Implementation of JATOS' public API for studies run by
 * PersonalMultipleWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublix extends Publix<PersonalMultipleWorker>
		implements IPublix {

	public static final String PERSONAL_MULTIPLE_WORKER_ID = "personalMultipleWorkerId";

	private static final String CLASS_NAME = PersonalMultiplePublix.class
			.getSimpleName();

	private final PersonalMultiplePublixUtils publixUtils;
	private final PersonalMultipleStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;

	@Inject
	PersonalMultiplePublix(JPAApi jpa, PersonalMultiplePublixUtils publixUtils,
			PersonalMultipleStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, GroupService groupService,
			ChannelService channelService,
			PersonalMultipleErrorMessages errorMessages,
			StudyAssets studyAssets, ComponentResultDao componentResultDao,
			JsonUtils jsonUtils, StudyResultDao studyResultDao,
			GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, errorMessages, studyAssets, componentResultDao,
				jsonUtils, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		String workerId = getQueryString(PERSONAL_MULTIPLE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "batchId " + batchId + ", " + "workerId " + workerId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		PersonalMultipleWorker worker = publixUtils
				.retrieveTypedWorker(workerId);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		session(WORKER_ID, workerId);
		session(BATCH_ID, batch.getId().toString());
		session(STUDY_ASSETS, study.getDirName());

		groupService.finishStudyInAllPriorGroups(worker, study);
		publixUtils.finishAllPriorStudyResults(worker, study);
		resultCreator.createStudyResult(study, batch, worker);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
