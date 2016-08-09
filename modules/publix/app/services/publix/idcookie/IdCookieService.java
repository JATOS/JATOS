package services.publix.idcookie;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.IdCookieContainerFullException;
import exceptions.publix.InternalServerErrorPublixException;
import models.common.Batch;
import models.common.ComponentResult;
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
			StudyResult studyResult) throws BadRequestPublixException,
			InternalServerErrorPublixException {
		writeIdCookie(worker, batch, studyResult, null);
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeIdCookie(Worker worker, Batch batch,
			StudyResult studyResult, ComponentResult componentResult)
			throws BadRequestPublixException,
			InternalServerErrorPublixException {
		IdCookieContainer idCookieContainer = idCookieAccessor.extract();
		try {
			idCookieAccessor.write(idCookieContainer, batch, studyResult,
					componentResult, worker);
		} catch (IdCookieContainerFullException e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
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
