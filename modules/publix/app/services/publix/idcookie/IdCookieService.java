package services.publix.idcookie;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.IdCookieContainerFullException;
import exceptions.publix.InternalServerErrorPublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;

/**
 * Service class for ID cookie handling. It generates, extracts and discards ID
 * cookies. An ID cookie is used by the JATOS server to tell jatos.js about
 * several IDs the current study run has (e.g. worker ID, study ID, study result
 * ID). This cookie is created when the study run is started and discarded when
 * it's done.
 * 
 * @author Kristian Lange (2016)
 */
@Singleton
public class IdCookieService {

	private final IdCookieAccessor idCookieAccessor;

	@Inject
	public IdCookieService(IdCookieAccessor idCookieAccessor) {
		this.idCookieAccessor = idCookieAccessor;
	}

	public IdCookieContainer extractIdCookieContainer() {
		return idCookieAccessor.extract();
	}

	public IdCookie getIdCookie(Long studyResultId)
			throws BadRequestPublixException {
		IdCookie idCookie = extractIdCookieContainer()
				.findWithStudyResultId(studyResultId);
		if (idCookie == null) {
			throw new BadRequestPublixException(PublixErrorMessages
					.idCookieForThisStudyResultNotExists(studyResultId));
		}
		return idCookie;
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult) throws InternalServerErrorPublixException {
		writeIdCookie(worker, batch, studyResult, null);
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * Response object. Checks if there is an existing ID cookie with the same
	 * study result ID and if so overwrites it. If there isn't it checks if the
	 * max number of ID cookies is reached and if so overwrites the oldest one -
	 * or if not writes a new one.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult, ComponentResult componentResult)
			throws InternalServerErrorPublixException {
		IdCookieContainer idCookieContainer = idCookieAccessor.extract();
		try {
			String newIdCookieName = null;

			// Check if there is an existing IdCookie for this StudyResult
			IdCookie existingIdCookie = idCookieContainer
					.findWithStudyResultId(studyResult.getId());
			if (existingIdCookie != null) {
				newIdCookieName = existingIdCookie.getName();
			} else {
				newIdCookieName = getNewIdCookieName(idCookieContainer);
			}

			IdCookie newIdCookie = buildIdCookie(newIdCookieName, batch,
					studyResult, componentResult, worker);

			idCookieAccessor.write(newIdCookie);
		} catch (IdCookieContainerFullException e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Generates the name for a new IdCookie: If the max number of IdCookies is
	 * reached it reuses the name of the oldest IdCookie. If not it creates a
	 * new name.
	 */
	private String getNewIdCookieName(IdCookieContainer idCookieContainer)
			throws IdCookieContainerFullException {
		if (idCookieContainer.isFull()) {
			throw new IdCookieContainerFullException(
					PublixErrorMessages.IDCOOKIE_CONTAINER_FULL);
		}
		int newIndex = idCookieContainer.getNextAvailableIdCookieIndex();
		return IdCookie.ID_COOKIE_NAME + "_" + newIndex;
	}

	/**
	 * Builds an IdCookie from the given parameters. It accepts null values for
	 * ComponentResult and GroupResult (stored in StudyResult). All others must
	 * not be null.
	 */
	private IdCookie buildIdCookie(String name, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker) {
		IdCookie idCookie = new IdCookie();
		Study study = studyResult.getStudy();

		// ComponentResult might not yet be created
		if (componentResult != null) {
			Component component = componentResult.getComponent();
			idCookie.setComponentId(component.getId());
			idCookie.setComponentResultId(componentResult.getId());
			idCookie.setComponentPosition(
					study.getComponentPosition(component));
		}

		// Might not have a GroupResult because it's not a group study
		GroupResult groupResult = studyResult.getActiveGroupResult();
		if (groupResult != null) {
			idCookie.setGroupResultId(groupResult.getId());
		}

		idCookie.setBatchId(batch.getId());
		idCookie.setCreationTime(System.currentTimeMillis());
		idCookie.setName(name);
		idCookie.setStudyId(study.getId());
		idCookie.setStudyResultId(studyResult.getId());
		idCookie.setWorkerId(worker.getId());
		idCookie.setWorkerType(worker.getWorkerType());
		return idCookie;
	}

	/**
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie.
	 */
	public void discardIdCookie(Long studyResultId,
			IdCookieContainer idCookieContainer)
			throws BadRequestPublixException {
		idCookieAccessor.discard(idCookieContainer, studyResultId);
	}

	/**
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie.
	 */
	public void discardIdCookie(Long studyResultId)
			throws BadRequestPublixException {
		IdCookieContainer idCookieContainer = idCookieAccessor.extract();
		idCookieAccessor.discard(idCookieContainer, studyResultId);
	}

	/**
	 * Checks the creation time of each IdCookie in the given IdCookieContainer
	 * and returns the oldest one. Returns null if the IdCookieContainer is
	 * empty.
	 */
	public IdCookie getOldestIdCookie(IdCookieContainer idCookieContainer) {
		Long oldest = Long.MAX_VALUE;
		IdCookie oldestIdCookie = null;
		for (IdCookie idCookie : idCookieContainer) {
			Long creationTime = idCookie.getCreationTime();
			if (creationTime != null && creationTime < oldest) {
				oldest = creationTime;
				oldestIdCookie = idCookie;
			}
		}
		return oldestIdCookie;
	}

	/**
	 * Checks the creation time of each IdCookie in the given IdCookieContainer
	 * and returns the study result ID of the oldest one. Returns null if the
	 * IdCookieContainer is empty.
	 */
	public long getOldestIdCookiesStudyResultId(
			IdCookieContainer idCookieContainer) {
		IdCookie oldest = getOldestIdCookie(idCookieContainer);
		return (oldest != null) ? oldest.getStudyResultId() : null;
	}

}
