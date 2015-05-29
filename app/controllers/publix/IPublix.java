package controllers.publix;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import play.libs.F.Promise;
import play.mvc.Result;

/**
 * Interface to Publix the public API of JATOS. With these actions/methods the
 * studies and components (running in the browser / on the client side) can
 * communicate (start, finish components/studies, retrieve/persist data) with
 * the JATOS server (running on the server side).
 * 
 * @author Kristian Lange
 */
public interface IPublix {

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the study with the given ID, then automatically starts it's first
	 * component.
	 */
	public Result startStudy(Long studyId) throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component with the given componentId that belongs to the study
	 * with the studyId.
	 */
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the component in the given position that belongs to the study with
	 * the studyId.
	 */
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException;

	/**
	 * HTTP type: Normal GET request
	 * 
	 * Starts the next component of the study with the given ID. Components
	 * within a study are ordered. This method starts the component after the
	 * current one. If there are no more components in the study, the study will
	 * be finished successfully.
	 */
	public Result startNextComponent(Long studyId) throws PublixException;

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
	public Result getInitData(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException, IOException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Expects the study's session data in JSON format and sets them in the
	 * study result that belong to the specified study. Study session data are
	 * individual for each study run and determined by the study running in the
	 * browser. The study session data are different from Play's session and
	 * stored within the study results.
	 */
	public Result setStudySessionData(Long studyId) throws PublixException,
			JsonProcessingException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * Persists the submitted data in the ComponentResult specified by the given
	 * study and component ID.
	 */
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request
	 * 
	 * Finishes the component specified by the given study and component ID.
	 * Optionally it can be specified whether the component was successful and
	 * and error message.
	 */
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Aborts the study with the given ID (StudyResult state will be ABORTED).
	 * Optionally a message can be given describing the reasons for the
	 * abortion.
	 */
	public Result abortStudy(Long studyId, String message)
			throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request
	 * 
	 * Finishes the study with the given ID (StudyResult state will be FINISHED
	 * or FAIL). Optionally it can be specified whether the study was successful
	 * and an error message.
	 */
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException;

	/**
	 * HTTP type: Ajax POST request
	 * 
	 * In case the client side wants to log an error.
	 */
	public Result logError(Long studyId, Long componentId)
			throws BadRequestPublixException;

}
