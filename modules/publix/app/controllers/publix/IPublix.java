package controllers.publix;

import exceptions.publix.PublixException;
import models.common.Component;
import models.common.StudyResult;
import models.common.StudyLink;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;

/**
 * Interface to all *Publix classes. With these API methods studies and components (running in the
 * browser) can communicate (e.g. start, finish components/studies, retrieve/persist data, join a group) with the JATOS
 * server (running on the server side).
 *
 * Group and batch channel are handled by the ChannelInterceptor.
 *
 * @author Kristian Lange
 */
public interface IPublix {

    /**
     * HTTP type: GET request
     *
     * Starts the study. Then redirects to start the first component.
     */
    Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException;

    /**
     * HTTP type: GET request
     *
     * Starts the component
     */
    Result startComponent(Http.Request request, StudyResult studyResult, Component component, String message)
            throws PublixException;

    /**
     * HTTP type: GET request
     *
     * Returns the init data provided by jatos.js to the experimenters code: study's properties, component's properties,
     * and study's session data.
     *
     * Hint: Study session data are individual for each study run and determined by the study running in the browser.
     * The study session data are different from Play's session and stored within the study results.
     */
    Result getInitData(Http.Request request, StudyResult studyResult, Component component)
            throws PublixException, IOException;

    /**
     * HTTP type: POST request
     *
     * Expects the study's session data in JSON format and sets them in the study result that belong to the specified
     * study. Study session data are individual for each study run and determined by the study running in the browser.
     * The study session data are different from Play's session and stored within the study results.
     */
    Result setStudySessionData(Http.Request request, StudyResult studyResult) throws PublixException;

    /**
     * HTTP type: POST request
     *
     * Heartbeat of a study result: used to give the user an idea of when the study run was last seen. jatos.js
     * periodically sends a request to this endpoint. The time when this request arrives is stored in StudyResult's
     * lastSeenDate field.
     */
    Result heartbeat(Http.Request request, StudyResult studyResult) throws PublixException;

    /**
     * HTTP type: PUT/POST request
     *
     * Persists the submitted data in the ComponentResult specified by the given study and component ID.
     * PUT: Already submitted data will be overwritten.
     * POST: Keeps the current data and appends to the end.
     */
    Result submitOrAppendResultData(Http.Request request, StudyResult studyResult, Component component, boolean append)
            throws PublixException;

    /**
     * HTTP type: POST request
     *
     * Uploads a result file
     */
    Result uploadResultFile(Http.Request request, StudyResult studyResult, Component component, String filename)
            throws PublixException;

    /**
     * HTTP type: GET request
     *
     * Downloads a result file that was previously uploded. One can limit the search to a specific componentId which is
     * useful if the same filename was used by different components.
     */
    Result downloadResultFile(Http.Request request, StudyResult studyResult, String filename, String componentId)
            throws PublixException;

    /**
     * HTTP type: GET request
     *
     * Aborts the study run (StudyResult state will be ABORTED). Optionally a message can be given describing the
     * reasons for the abortion.
     */
    Result abortStudy(Http.Request request, StudyResult studyResult, String message) throws PublixException;

    /**
     * HTTP type: GET request
     *
     * Finishes the study run (StudyResult state will be FINISHED or FAIL). Optionally it can be specified
     * whether the study run was successful and a message can be logged.
     */
    Result finishStudy(Http.Request request, StudyResult studyResult, Boolean successful, String message)
            throws PublixException;

    /**
     * HTTP type: POST request
     *
     * With this method the client side can log into the server's log.
     */
    Result log(Http.Request request, StudyResult studyResult, Component component) throws PublixException;

}
