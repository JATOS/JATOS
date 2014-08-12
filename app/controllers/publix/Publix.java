package controllers.publix;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import controllers.Users;

/**
 * Abstract controller class that defines the public API for managing a study
 * and its components. Additionally it has a few common methods. common methods.
 * 
 * @author madsen
 */
public abstract class Publix extends Controller {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";
	private static final String CLASS_NAME = Publix.class.getSimpleName();

	/**
	 * HTTP type: Normal GET request
	 */
	public Result index() {
		return ok();
	}

	/**
	 * HTTP type: Normal GET request
	 */
	public abstract Result startStudy(Long studyId) throws Exception;

	/**
	 * HTTP type: Normal GET request
	 */
	public abstract Result startComponent(Long studyId, Long componentId)
			throws Exception;

	/**
	 * HTTP type: Normal GET request
	 */
	public abstract Result startNextComponent(Long studyId)
			throws Exception;

	/**
	 * HTTP type: Ajax GET request
	 */
	public abstract Result getComponentData(Long studyId, Long componentId)
			throws Exception;

	/**
	 * HTTP type: Ajax POST request
	 */
	public abstract Result submitResultData(Long studyId, Long componentId)
			throws Exception;

	/**
	 * HTTP type: Normal GET request
	 * successful and errorMsg are optional
	 */
	public abstract Result finishStudy(Long studyId, Boolean successful,
			String errorMsg) throws Exception;

	/**
	 * In case the client side wants to log an error.<br>
	 * HTTP type: Ajax GET request
	 */
	public Result logError() {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " logging client-side error: " + msg);
		return ok();
	}

	public static String getWorkerIdFromSession() {
		return session(Publix.WORKER_ID);
	}

	public static String getLoggedInUserEmail() {
		return session(Users.COOKIE_EMAIL);
	}

}
