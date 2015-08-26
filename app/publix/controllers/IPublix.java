package publix.controllers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

import play.libs.F.Promise;
import play.mvc.Result;
import play.mvc.WebSocket;
import publix.exceptions.BadRequestPublixException;
import publix.exceptions.PublixException;

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
	 * Starts the study with the given ID, then automatically starts it's first
	 * component.
	 */
	Result startStudy(Long studyId) throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component with the given componentId that belongs to the study
	 * with the studyId.
	 */
	Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component in the given position that belongs to the study with
	 * the studyId.
	 */
	Promise<Result> startComponentByPosition(Long studyId, Integer position)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the next component of the study with the given ID. Components
	 * within a study are ordered. This method starts the component after the
	 * current one. If there are no more components in the study, the study will
	 * be finished successfully.
	 */
	Result startNextComponent(Long studyId) throws PublixException;

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
	Result getInitData(Long studyId, Long componentId) throws PublixException,
			IOException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Let the worker join a group. Only works if this study is a group study.
	 */
	Result joinGroup(Long studyId) throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Let the worker drop out of the group he joined before. Only works if this
	 * study is a group study.
	 */
	Result dropGroup(Long studyId) throws PublixException;

	/**
	 * WebSocket
	 * 
	 * Opens a websocket between JATOS and the client to exchange system
	 * messages (e.g. announce group is complete or new group member). Only
	 * works if this study is a group study.
	 */
	WebSocket<String> openSystemChannel(Long studyId)
			throws BadRequestPublixException;

	/**
	 * WebSocket
	 * 
	 * Opens a websocket between JATOS and the client to exchange group
	 * messages. JATOS just forwards messages coming from one group member to
	 * all other members. Only works if this study is a group study.
	 */
	WebSocket<JsonNode> openGroupChannel(Long studyId)
			throws BadRequestPublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Expects the study's session data in JSON format and sets them in the
	 * study result that belong to the specified study. Study session data are
	 * individual for each study run and determined by the study running in the
	 * browser. The study session data are different from Play's session and
	 * stored within the study results.
	 */
	Result setStudySessionData(Long studyId) throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Persists the submitted data in the ComponentResult specified by the given
	 * study and component ID.
	 */
	Result submitResultData(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Finishes the component specified by the given study and component ID.
	 * Optionally it can be specified whether the component was successful and
	 * and error message.
	 */
	Result finishComponent(Long studyId, Long componentId, Boolean successful,
			String errorMsg) throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Aborts the study with the given ID (StudyResult state will be ABORTED).
	 * Optionally a message can be given describing the reasons for the
	 * abortion.
	 */
	Result abortStudy(Long studyId, String message) throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Finishes the study with the given ID (StudyResult state will be FINISHED
	 * or FAIL). Optionally it can be specified whether the study was successful
	 * and an error message.
	 */
	Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * In case the client side wants to log an error.
	 */
	Result logError(Long studyId, Long componentId)
			throws BadRequestPublixException;

}
