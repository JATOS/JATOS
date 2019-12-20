package controllers.publix;

import exceptions.publix.PublixException;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;

/**
 * Interface to Publix the public, RESTful API of JATOS. With these methods the studies and components (running in the
 * browser) can communicate (e.g. start, finish components/studies, retrieve/persist data, join a group) with the JATOS
 * server (running on the server side).
 * <p>
 * Group and batch channel are handled by the ChannelInterceptor.
 *
 * @author Kristian Lange
 */
public interface IPublix {

    /**
     * HTTP type: Normal GET request
     * <p>
     * Starts the study within a batch. Then automatically starts it's first component.
     */
    Result startStudy(Long studyId, Long batchId) throws PublixException;

    /**
     * HTTP type: Normal GET request
     * <p>
     * Starts the component with the given componentId that belongs to the study with the studyId.
     */
    Result startComponent(Long studyId, Long componentId, Long studyResultId, String message) throws PublixException;

    /**
     * HTTP type: Ajax GET request
     * <p>
     * Returns the study's properties, component's properties, and study's session data in JSON format.
     * <p>
     * Hint: Study session data are individual for each study run and determined by the study running in the browser.
     * The study session data are different from Play's session and stored within the study results.
     */
    Result getInitData(Long studyId, Long componentId, Long studyResultId) throws PublixException, IOException;

    /**
     * HTTP type: Ajax POST request
     * <p>
     * Expects the study's session data in JSON format and sets them in the study result that belong to the specified
     * study. Study session data are individual for each study run and determined by the study running in the browser.
     * The study session data are different from Play's session and stored within the study results.
     */
    Result setStudySessionData(Long studyId, Long studyResultId) throws PublixException;

    /**
     * HTTP type: Ajax POST request
     * <p>
     * Heartbeat of a study result: when was the study run last seen. jatos.js periodically sends an Ajax request to
     * this endpoint. The time when this request arrives is stored in StudyResult's lastSeenDate field.
     */
    Result heartbeat(Long studyId, Long studyResultId) throws PublixException;

    /**
     * HTTP type: Ajax PUT request
     * <p>
     * Persists the submitted data in the ComponentResult specified by the given study and component ID. Already
     * submitted data will be overwritten.
     */
    Result submitResultData(Long studyId, Long componentId, Long studyResultId) throws PublixException;

    /**
     * HTTP type: Ajax POST request
     * <p>
     * Appends the submitted data to the already stored result data in the ComponentResult specified by the given study
     * and component ID.
     */
    Result appendResultData(Long studyId, Long componentId, Long studyResultId) throws PublixException;

    /**
     * HTTP type: POST request
     * <p>
     * Uploads a result file
     */
    Result uploadResultFile(Http.Request request, Long studyId, Long componentId, Long studyResultId, String filename)
            throws PublixException;

    /**
     * HTTP type: GET request
     * <p>
     * Downloads a result file
     */
    Result downloadResultFile(Http.Request request, Long studyId, Long componentId, Long studyResultId, String filename)
            throws PublixException;

    /**
     * HTTP type: Normal or Ajax GET request
     * <p>
     * Aborts the study with the given ID (StudyResult state will be ABORTED). Optionally a message can be given
     * describing the reasons for the abortion.
     */
    Result abortStudy(Long studyId, Long studyResultId, String message) throws PublixException;

    /**
     * HTTP type: Normal or Ajax GET request
     * <p>
     * Finishes the study with the given ID (StudyResult state will be FINISHED or FAIL). Optionally it can be specified
     * whether the study was successful and a message to be logged.
     */
    Result finishStudy(Long studyId, Long studyResultId, Boolean successful, String message) throws PublixException;

    /**
     * HTTP type: Ajax POST request
     * <p>
     * With this method the client side can log into the server's log.
     */
    Result log(Long studyId, Long componentId, Long studyResultId) throws PublixException;

}
