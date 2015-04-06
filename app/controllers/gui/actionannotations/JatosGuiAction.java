package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import play.Logger;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.SimpleResult;
import play.mvc.With;
import services.FlashScopeMessaging;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import exceptions.gui.JatosGuiException;

/**
 * For all actions in a controller that is annotated with @JatosGui catch
 * {@link JatosGuiException} and return the SimpleResult stored in the exception
 * (e.g. display an error page). If there is another Exception than
 * JatosGuiException catch it too and redirect to the home view with an internal
 * error, so no internal exception message ever is displayed in the GUI.
 * 
 * @author Kristian Lange
 */
public class JatosGuiAction extends Action<JatosGui> {

	@With(JatosGuiAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface JatosGui {
	}

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
