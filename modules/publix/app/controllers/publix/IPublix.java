package controllers.publix;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

import exceptions.publix.PublixException;
import play.mvc.Result;
import play.mvc.WebSocket;

/**
 * Interface to Publix the public API of JATOS. With these actions/methods the
 * studies and components (running in the browser / on the client side) can
 * communicate (start, finish components/studies, retrieve/persist data) with
 * the JATOS server (running on the server side).
 * 
 * The Publix can be seen as a state machine. State is stored in the
 * StudyResults and ComponentResults. The methods of Publix react differently
 * based on the state.
 * 
 * @author Kristian Lange
 */
public interface IPublix {

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the study within a batch. Then automatically starts it's first
	 * component.
	 */
	Result startStudy(Long studyId, Long batchId) throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component with the given componentId that belongs to the study
	 * with the studyId.
	 */
	Result startComponent(Long studyId, Long componentId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component in the given position that belongs to the study with
	 * the studyId.
	 */
	Result startComponentByPosition(Long studyId, Integer position,
			Long studyResultId) throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the next component of the study with the given ID. Components
	 * within a study are ordered. This method starts the component after the
	 * current one. If there are no more components in the study, the study will
	 * be finished successfully.
	 */
	Result startNextComponent(Long studyId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Returns the study's properties, component's properties, and study's
	 * session data in JSON format.
	 * 
	 * Hint: Study session data are individual for each study run and determined
	 * by the study running in the browser. The study session data are different
	 * from Play's session and stored within the study results.
	 */
	Result getInitData(Long studyId, Long componentId, Long studyResultId)
			throws PublixException, IOException;

	/**
	 * HTTP type: WebSocket
	 * 
	 * Let the worker (actually it's StudyResult) join a group (actually a
	 * GroupResult) and open a WebSocket (group channel). Only works if this
	 * study is a group study. All group data are stored in a GroupResult and
	 * the group channels will be handled by a GroupDispatcher which uses Akka.
	 * 
	 * @param studyId
	 *            Study's ID
	 * @return WebSocket that transports JSON strings.
	 * @throws PublixException
	 */
	WebSocket<JsonNode> joinGroup(Long studyId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Try to find a different group for this StudyResult. It reuses the already
	 * opened group channel and just reassigns it to a different group (or in
	 * more detail to a different GroupResult and GroupDispatcher). If it is
	 * successful it returns an 200 (OK) HTTP status code. If it can't find any
	 * other group it returns a 204 (NO CONTENT) HTTP status code.
	 * 
	 * @param studyId
	 *            Study's ID
	 * @throws PublixException
	 */
	Result reassignGroup(Long studyId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Let the worker leave the group (actually a GroupResult) he joined before
	 * and closes the group channel. Only works if this study is a group study.
	 */
	Result leaveGroup(Long studyId, Long studyResultId) throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Expects the study's session data in JSON format and sets them in the
	 * study result that belong to the specified study. Study session data are
	 * individual for each study run and determined by the study running in the
	 * browser. The study session data are different from Play's session and
	 * stored within the study results.
	 */
	Result setStudySessionData(Long studyId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Persists the submitted data in the ComponentResult specified by the given
	 * study and component ID.
	 */
	Result submitResultData(Long studyId, Long componentId, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Finishes the component specified by the given study and component ID.
	 * Optionally it can be specified whether the component was successful and
	 * and error message.
	 */
	Result finishComponent(Long studyId, Long componentId, Boolean successful,
			String errorMsg, Long studyResultId) throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Aborts the study with the given ID (StudyResult state will be ABORTED).
	 * Optionally a message can be given describing the reasons for the
	 * abortion.
	 */
	Result abortStudy(Long studyId, String message, Long studyResultId)
			throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Finishes the study with the given ID (StudyResult state will be FINISHED
	 * or FAIL). Optionally it can be specified whether the study was successful
	 * and an error message.
	 */
	Result finishStudy(Long studyId, Boolean successful, String errorMsg,
			Long studyResultId) throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * With this method the client side can log into the server's log.
	 */
	Result log(Long studyId, Long componentId) throws PublixException;

}
