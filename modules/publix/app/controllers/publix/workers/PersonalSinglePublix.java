package controllers.publix.workers;

import batch.BatchChannelService;
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
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import session2.group.GroupAdministration;
import session2.group.GroupChannelService;
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
			ResultCreator resultCreator,
			BatchChannelService batchChannelService,
			GroupAdministration groupAdministration,
			GroupChannelService groupChannelService,
			IdCookieService idCookieService,
			PersonalSingleErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, batchChannelService,
				groupAdministration, groupChannelService, idCookieService,
				errorMessages, studyAssets, jsonUtils, componentResultDao,
				studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
	}

	/**
	 * {@inheritDoc}<br>
	 * <br>
	 * 
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
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		String workerIdStr = HttpUtils
				.getQueryString(PERSONAL_SINGLE_WORKER_ID);
		boolean pre = HttpUtils.getQueryString("pre") != null;
		LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
				+ batchId + ", " + PERSONAL_SINGLE_WORKER_ID + " " + workerIdStr
				+ ", " + "pre " + pre);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		PersonalSingleWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

		StudyResult studyResult;
		if (worker.getStudyResultList().isEmpty()) {
			publixUtils.finishAbandonedStudyResults();
			studyResult = resultCreator.createStudyResult(study, batch, worker);
			idCookieService.writeIdCookie(worker, batch, studyResult);
		} else {
			studyResult = worker.getLastStudyResult();
		}
		publixUtils.setPreStudyStateByPre(pre, studyResult);

		Component component = publixUtils.retrieveFirstActiveComponent(study);
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, component.getId(), studyResult.getId()));
	}

}
