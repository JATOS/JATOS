package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import controllers.gui.actionannotations.GuiExceptionAction.GuiExceptionCatching;
import exceptions.gui.JatosGuiException;
import play.Logger;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.common.HttpUtils;

/**
 * For all actions in a controller that is annotated with @JatosGui catch
 * {@link JatosGuiException} and return the Result stored in the exception (e.g.
 * display an error page). If there is another Exception than JatosGuiException
 * catch it too and redirect to the home view with an internal error, so no
 * internal exception message ever is displayed in the GUI.
 * 
 * @author Kristian Lange
 */
public class GuiExceptionAction extends Action<GuiExceptionCatching> {

	@With(GuiExceptionAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface GuiExceptionCatching {
	}

	public F.Promise<Result> call(Http.Context ctx) throws Throwable {
		Promise<Result> call;
		try {
			call = delegate.call(ctx);
		} catch (JatosGuiException e) {
			Logger.info("JatosGuiException during call "
					+ Controller.request().uri() + ": " + e.getMessage());
			Result result = e.getSimpleResult();
			call = Promise.pure(result);
		} catch (Exception e) {
			Logger.error("Exception during call " + Controller.request().uri()
					+ ": " + e.getMessage(), e);
			if (HttpUtils.isAjax()) {
				call = Promise
						.<Result>pure(internalServerError(e.getMessage()));
			} else {
				call = Promise.<Result>pure(
						internalServerError("Internal JATOS error during "
								+ Controller.request().uri() + ": "
								+ e.toString()));
			}
		}
		return call;
	}

}
