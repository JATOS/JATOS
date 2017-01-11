package services.publix.idcookie;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;
import utils.common.HttpUtils;

/**
 * Service class for JATOS ID cookie handling. It generates, extracts and
 * discards ID cookies. An ID cookie is used by the JATOS server to tell
 * jatos.js about several IDs the current study run has (e.g. worker ID, study
 * ID, study result ID). This cookie is created when the study run is started
 * and discarded when it's done.
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

	/**
	 * Returns the IdCookie that corresponds to the given study result ID. If
	 * the cookie doesn't exist it throws a BadRequestPublixException.
	 */
	public IdCookie getIdCookie(Long studyResultId)
			throws BadRequestPublixException,
			InternalServerErrorPublixException {
		IdCookie idCookie = getIdCookieCollection()
				.findWithStudyResultId(studyResultId);
		if (idCookie == null) {
			throw new BadRequestPublixException(PublixErrorMessages
					.idCookieForThisStudyResultNotExists(studyResultId));
		}
		return idCookie;
	}

	/**
	 * Returns the whole IdCookieCollection
	 */
	public IdCookieCollection getIdCookieCollection()
			throws InternalServerErrorPublixException {
		try {
			return idCookieAccessor.extract();
		} catch (IdCookieAlreadyExistsException e) {
			// Should never happen or something is seriously wrong
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Returns true if the study assets of at least one ID cookie is equal to
	 * the given study assets. Otherwise returns false.
	 */
	public boolean oneIdCookieHasThisStudyAssets(String studyAssets)
			throws InternalServerErrorPublixException {
		for (IdCookie idCookie : getIdCookieCollection().getAll()) {
			if (idCookie.getStudyAssets().equals(studyAssets)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult) throws InternalServerErrorPublixException {
		writeIdCookie(worker, batch, studyResult, null, null);
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult, ComponentResult componentResult)
			throws InternalServerErrorPublixException {
		writeIdCookie(worker, batch, studyResult, componentResult, null);
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult, JatosRun jatosRun)
			throws InternalServerErrorPublixException {
		writeIdCookie(worker, batch, studyResult, null, jatosRun);
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * Response object. Checks if there is an existing ID cookie with the same
	 * study result ID and if so overwrites it. If there isn't it writes a new
	 * one. It expects a free spot in the cookie collection and if not throws an
	 * InternalServerErrorPublixException (should never happen). The deletion of
	 * the oldest cookie must have happened beforehand.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			JatosRun jatosRun) throws InternalServerErrorPublixException {
		IdCookieCollection idCookieCollection = getIdCookieCollection();
		try {
			String newIdCookieName = null;

			// Check if there is an existing IdCookie for this StudyResult
			IdCookie existingIdCookie = idCookieCollection
					.findWithStudyResultId(studyResult.getId());
			if (existingIdCookie != null) {
				newIdCookieName = existingIdCookie.getName();
			} else {
				newIdCookieName = getNewIdCookieName(idCookieCollection);
			}

			IdCookie newIdCookie = buildIdCookie(newIdCookieName, batch,
					studyResult, componentResult, worker, jatosRun);

			idCookieAccessor.write(newIdCookie);
		} catch (IdCookieCollectionFullException
				| IdCookieAlreadyExistsException e) {
			// Should never happen since we check in front
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Generates the name for a new IdCookie: If the max number of IdCookies is
	 * reached it reuses the name of the oldest IdCookie. If not it creates a
	 * new name.
	 */
	private String getNewIdCookieName(IdCookieCollection idCookieCollection)
			throws IdCookieCollectionFullException {
		if (idCookieCollection.isFull()) {
			throw new IdCookieCollectionFullException(
					PublixErrorMessages.IDCOOKIE_COLLECTION_FULL);
		}
		int newIndex = idCookieCollection.getNextAvailableIdCookieIndex();
		return IdCookie.ID_COOKIE_NAME + "_" + newIndex;
	}

	/**
	 * Builds an IdCookie from the given parameters. It accepts null values for
	 * ComponentResult and GroupResult (stored in StudyResult). All others must
	 * not be null.
	 */
	private IdCookie buildIdCookie(String name, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker, JatosRun jatosRun) {
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
		idCookie.setStudyAssets(HttpUtils.urlEncode(study.getDirName()));
		idCookie.setName(name);
		idCookie.setStudyId(study.getId());
		idCookie.setStudyResultId(studyResult.getId());
		idCookie.setWorkerId(worker.getId());
		idCookie.setWorkerType(worker.getWorkerType());
		idCookie.setJatosRun(jatosRun);
		return idCookie;
	}

	/**
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie.
	 */
	public void discardIdCookie(Long studyResultId)
			throws BadRequestPublixException,
			InternalServerErrorPublixException {
		try {
			idCookieAccessor.discard(studyResultId);
		} catch (IdCookieAlreadyExistsException e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Returns true if the max number of IdCookies have been reached and false
	 * otherwise.
	 */
	public boolean maxIdCookiesReached()
			throws InternalServerErrorPublixException {
		try {
			return idCookieAccessor.extract().isFull();
		} catch (IdCookieAlreadyExistsException e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Checks the creation time of each IdCookie in the given IdCookieCollection
	 * and returns the oldest one. Returns null if the IdCookieCollection is
	 * empty.
	 */
	public IdCookie getOldestIdCookie()
			throws InternalServerErrorPublixException {
		IdCookieCollection idCookieCollection = getIdCookieCollection();
		Long oldest = Long.MAX_VALUE;
		IdCookie oldestIdCookie = null;
		for (IdCookie idCookie : idCookieCollection.getAll()) {
			Long creationTime = idCookie.getCreationTime();
			if (creationTime != null && creationTime < oldest) {
				oldest = creationTime;
				oldestIdCookie = idCookie;
			}
		}
		return oldestIdCookie;
	}

	/**
	 * Checks the creation time of each IdCookie in the given IdCookieCollection
	 * and returns the study result ID of the oldest one. Returns null if the
	 * IdCookieCollection is empty.
	 */
	public Long getStudyResultIdFromOldestIdCookie()
			throws InternalServerErrorPublixException {
		IdCookie oldest = getOldestIdCookie();
		return (oldest != null) ? oldest.getStudyResultId() : null;
	}

}
