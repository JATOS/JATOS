package controllers.publix;

import com.fasterxml.jackson.core.JsonProcessingException;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import play.libs.F.Promise;
import play.mvc.Result;

/**
 * Public API of JATOS. With these methods the studies and components running on
 * the client side can communicate (start, finish components/studies,
 * retrieve/persist data) with JATOS running on the server side.
 * 
 * @author Kristian Lange
 */
public interface IPublix {

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the study with the given ID, then starts it's first component.
	 */
	public Result startStudy(Long studyId) throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the component with the given componentId that belongs to the study
	 * with the studyId.
	 */
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the component in the given position that belongs to the study with
	 * the studyId.
	 */
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the next component of the study with the given ID. Components
	 * within a study are ordered. This method starts the component after the
	 * current one. If there are no more components in the study, the study will
	 * be finished successfully.
	 */
	public Result startNextComponent(Long studyId) throws PublixException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Returns the properties in JSON format that belong to the specified study.
	 */
	public Result getStudyProperties(Long studyId) throws PublixException,
			JsonProcessingException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Returns the study's session data in JSON format that belong to the
	 * specified study. Study session data are individual for each study run.
	 * The study session data are different from Play's session and stored
	 * within the study results.
	 */
	public Result getStudySessionData(Long studyId) throws PublixException,
			JsonProcessingException;

	/**
	 * HTTP type: Ajax POST request<br>
	 * Expects the study's session data in JSON format and sets them in the
	 * study result that belong to the specified study. Study session data are
	 * individual for each study run. The study session data are different from
	 * Play's session and stored within the study results.
	 */
	public Result setStudySessionData(Long studyId) throws PublixException,
			JsonProcessingException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Returns the properties in JSON format that belong to the component
	 * specified by its ID.
	 */
	public Result getComponentProperties(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException;

	/**
	 * HTTP type: Ajax POST request<br>
	 * Persists the submitted data together with the component specified by its
	 * ID.
	 */
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Finishes the component with the given ID. Optionally it can be specified
	 * whether the component was successful and and error message.
	 */
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request<br>
	 * Aborts the study with the given ID (StudyResult state will be ABORTED).
	 * Optionally a message can be given describing the reasons for the
	 * abortion.
	 */
	public Result abortStudy(Long studyId, String message)
			throws PublixException;

	/**
	 * HTTP type: Normal or Ajax GET request<br>
	 * Finishes the study with the given ID (StudyResult state will be FINISHED
	 * or FAIL). Optionally it can be specified whether the study was successful
	 * and an error message.
	 */
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException;

	/**
	 * HTTP type: Ajax POST request<br>
	 * In case the client side wants to log an error.
	 */
	public Result logError(Long studyId, Long componentId)
			throws BadRequestPublixException;

}
