package controllers;

import play.Logger;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Http;
import play.mvc.Result;
import exceptions.JatosGuiException;

/**
 * For all actions in a controller that is annotated with JatosGuiAction catch
 * {@link JatosGuiException} and return the Result stored in the exception
 * (e.g. display an error page).
 * 
 * @author Kristian Lange
 */
public class JatosGuiAction extends play.mvc.Action.Simple {

	public F.Promise<Result> call(Http.Context ctx) throws Throwable {
		Promise<Result> call;
		try {
			call = delegate.call(ctx);
		} catch (JatosGuiException e) {
			Logger.info("JatosGuiException: " + e.getMessage());
			Result result = e.getSimpleResult();
			call = Promise.pure(result);
		}
		return call;
	}

}
