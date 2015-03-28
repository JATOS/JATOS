package controllers.gui.actionannotations;

import play.Logger;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.SimpleResult;
import services.FlashScopeMessaging;
import exceptions.gui.JatosGuiException;

/**
 * For all actions in a controller that is annotated with JatosGui catch
 * {@link JatosGuiException} and return the SimpleResult stored in the exception
 * (e.g. display an error page). If there is another Exception than
 * JatosGuiException catch it too and redirect to the home view with an internal
 * error, so no internal exception message ever is displayed in the GUI. 
 * 
 * @author Kristian Lange
 */
public class JatosGuiAction extends Action<JatosGui> {

	public F.Promise<SimpleResult> call(Http.Context ctx) throws Throwable {
		Promise<SimpleResult> call;
		try {
			call = delegate.call(ctx);
		} catch (JatosGuiException e) {
			Logger.info("JatosGuiException: " + e.getMessage());
			SimpleResult result = e.getSimpleResult();
			call = Promise.<SimpleResult> pure(result);
		} catch (Exception e) {
			Logger.error(e.toString());
			FlashScopeMessaging
					.error("Internal JATOS error: " + e.getMessage());
			call = Promise
					.<SimpleResult> pure(redirect(controllers.gui.routes.Home
							.home()));
		}
		return call;
	}

}
