package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.common.BadRequestException;
import models.common.ComponentResult;
import models.common.StudyResult;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exceptions.IdCookieCollectionFullException;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class for JATOS ID cookie handling. It generates, extracts and discards ID cookies. An ID cookie is used by
 * the JATOS server to tell jatos.js about several IDs the current study run has (e.g., worker ID, study ID, study
 * result ID). This cookie is created when the study run is started and discarded when it's done. If all possible
 * cookies are used, the start of a new study will cause the oldest ID cookie to be overwritten.
 *
 * @author Kristian Lange
 */
@Singleton
public class IdCookieService {

    private final IdCookieAccessor idCookieAccessor;

    @Inject
    public IdCookieService(IdCookieAccessor idCookieAccessor) {
        this.idCookieAccessor = idCookieAccessor;
    }

    public boolean hasIdCookie(Http.RequestHeader requestHeader, Long studyResultId) {
        return getIdCookieCollection(requestHeader).findWithStudyResultId(studyResultId) != null;
    }

    /**
     * Returns the IdCookieModel that corresponds to the given study result ID. If the cookie doesn't exist, it throws a
     * BadRequestException.
     */
    public IdCookieModel getIdCookie(Http.RequestHeader requestHeader, Long studyResultId) {
        IdCookieModel idCookie = getIdCookieCollection(requestHeader).findWithStudyResultId(studyResultId);
        if (idCookie == null) {
            throw new BadRequestException(PublixErrorMessages.idCookieForThisStudyResultNotExists(studyResultId));
        }
        return idCookie;
    }

    /**
     * Returns the whole IdCookieCollection
     */
    public IdCookieCollection getIdCookieCollection(Http.RequestHeader requestHeader) {
        return idCookieAccessor.extract(requestHeader);
    }

    /**
     * Returns true if the study assets of at least one ID cookie are equal to the given study assets. Otherwise,
     * returns false.
     */
    public boolean oneIdCookieHasThisStudyAssets(Http.RequestHeader requestHeader, String studyAssets) {
        for (IdCookieModel idCookie : getIdCookieCollection(requestHeader).getAll()) {
            if (idCookie.getStudyAssets().equals(studyAssets)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(Http.RequestHeader requestHeader, StudyResult studyResult) {
        writeIdCookie(requestHeader, studyResult, null, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(Http.RequestHeader requestHeader, StudyResult studyResult, ComponentResult componentResult) {
        writeIdCookie(requestHeader, studyResult, componentResult, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(Http.RequestHeader requestHeader, StudyResult studyResult, JatosRun jatosRun) {
        writeIdCookie(requestHeader, studyResult, null, jatosRun);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the Response object. Checks if there is an
     * existing ID cookie with the same study result ID and if so, overwrites it. If there isn't, it writes a new one.
     * It expects a free spot in the cookie collection and if not, throws an InternalServerErrorPublixException (should
     * never happen). The deletion of the oldest cookie must have happened beforehand.
     */
    public void writeIdCookie(Http.RequestHeader requestHeader, StudyResult studyResult, ComponentResult componentResult,
                              JatosRun jatosRun) {
        IdCookieCollection idCookieCollection = getIdCookieCollection(requestHeader);
        if (idCookieCollection.isFull()) {
            throw new IdCookieCollectionFullException(PublixErrorMessages.IDCOOKIE_COLLECTION_FULL);
        }

        IdCookieModel newIdCookie = new IdCookieModel(studyResult, componentResult, jatosRun);
        idCookieAccessor.write(requestHeader, newIdCookie);
    }

    /**
     * Discards the ID cookie if the given study result ID is equal to the one in the cookie.
     */
    public void discardIdCookie(Http.RequestHeader requestHeader, Long studyResultId) {
        idCookieAccessor.discard(requestHeader, studyResultId);
    }

    /**
     * Returns true if the max number of ID cookies has been reached and false otherwise.
     */
    public boolean maxIdCookiesReached(Http.RequestHeader requestHeader) {
        return idCookieAccessor.extract(requestHeader).isFull();
    }

    /**
     * Checks the creation time of each ID cookie in the given IdCookieCollection and returns the oldest one. Returns
     * null if the IdCookieCollection is empty.
     */
    public IdCookieModel getOldestIdCookie(Http.RequestHeader requestHeader) {
        IdCookieCollection idCookieCollection = getIdCookieCollection(requestHeader);
        long oldest = Long.MAX_VALUE;
        IdCookieModel oldestIdCookie = null;
        for (IdCookieModel idCookie : idCookieCollection.getAll()) {
            Long creationTime = idCookie.getCreationTime();
            if (creationTime != null && creationTime < oldest) {
                oldest = creationTime;
                oldestIdCookie = idCookie;
            }
        }
        return oldestIdCookie;
    }

    /**
     * Checks the creation time of each ID cookie in the given IdCookieCollection and returns the study result ID of the
     * oldest one. Returns null if the IdCookieCollection is empty.
     */
    public Long getStudyResultIdFromOldestIdCookie(Http.RequestHeader requestHeader) {
        IdCookieModel oldest = getOldestIdCookie(requestHeader);
        return (oldest != null) ? oldest.getStudyResultId() : null;
    }

    public JatosRun getJatosRun(Http.RequestHeader requestHeader, Long studyResultId) {
        return getIdCookie(requestHeader, studyResultId).getJatosRun();
    }

}
