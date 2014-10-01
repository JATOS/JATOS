package controllers.publix;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Abstract controller class for all controller that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
public abstract class Publix extends Controller implements IPublix {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";
	private static final String CLASS_NAME = Publix.class.getSimpleName();

	@Override
	public Result logError() {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " - logging client-side error: " + msg);
		return ok();
	}
	
	@Override
	public Result teapot() {
		return status(418, "I'm a teapot");
	}

}
