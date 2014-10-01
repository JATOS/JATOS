package controllers.publix;

import com.fasterxml.jackson.core.JsonProcessingException;

import exceptions.PublixException;
import play.mvc.Result;

/**
 * Public API of MechArg. With these methods the studies and components running
 * on the client side can communicate (start, finish components/studies,
 * retrieve/persist data) with the MechArg running on the server side.
 * 
 * @author Kristian Lange
 */
public interface IPublix {

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the study with the given id, then starts it's first component.
	 */
	public Result startStudy(Long studyId) throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the component with the given componentId that belongs to the study
	 * with the studyId.
	 */
	public Result startComponent(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Starts the next component of the study with the given id. Components
	 * within a study are ordered. This method starts the component after the
	 * current one. If there are no more components in the study, the study will
	 * be finished successfully.
	 */
	public Result startNextComponent(Long studyId) throws PublixException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Returns the data in JSON format that belong to the specified study.
	 */
	public Result getStudyData(Long studyId) throws PublixException,
			JsonProcessingException;

	/**
	 * HTTP type: Ajax GET request<br>
	 * Returns the data in JSON format that belong to the specified component.
	 */
	public Result getComponentData(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException;

	/**
	 * HTTP type: Ajax POST request<br>
	 * Persists the submitted data together with the component.
	 */
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException;

	/**
	 * HTTP type: Normal GET request<br>
	 * Finishes the study with the given id. Optionally it can be specified
	 * whether the study was successful and and error message.
	 */
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException;

	/**
	 * HTTP type: Ajax POST request<br>
	 * In case the client side wants to log an error.
	 */
	public Result logError();

	/**
	 * HTTP type: Normal GET request<br>
	 */
	public Result teapot();

}
