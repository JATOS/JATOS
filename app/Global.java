import java.lang.reflect.Method;

import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.Results;
import play.mvc.SimpleResult;
import controllers.Components;
import controllers.Studies;
import controllers.TryPublix;
import controllers.publix.Publix;
import exceptions.PublixException;
import exceptions.ResultException;

public class Global extends GlobalSettings {

	@Override
	public Action onRequest(Request request, final Method actionMethod) {
		// Check if this is a request for the Publix class but originates from
		// within MechArg and wants to try a component or study
		String playCookie = request.cookie("PLAY_SESSION").value();
		boolean isForPublix = actionMethod.getDeclaringClass().equals(
				Publix.class);
		boolean isFromMechArg = playCookie
				.contains(Components.MECHARG_COMPONENTTRY)
				|| playCookie.contains(Studies.MECHARG_STUDYTRY);
		if (isForPublix && isFromMechArg) {
			return createTryAction(actionMethod);
		}
		return super.onRequest(request, actionMethod);
	}

	/**
	 * Creates an action that instead of calling the method in Publix, calls the
	 * method of the same name in TryPublix.
	 */
	private Action createTryAction(final Method actionMethod) {
		return new Action.Simple() {
			@Override
			public Promise<SimpleResult> call(play.mvc.Http.Context ctx)
					throws Throwable {
				String methodName = actionMethod.getName();
				Class<?>[] parameterTypes = actionMethod.getParameterTypes();
				SimpleResult result = (SimpleResult) TryPublix.class.getMethod(
						methodName, parameterTypes).invoke(null, 1l, 1l);
				return Promise.<SimpleResult> pure(result);
			}
		};
	}

	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable t) {
		Throwable cause = t.getCause();
		Throwable causeCause = t.getCause().getCause();
		if (cause instanceof PublixException
				|| causeCause instanceof PublixException) {
			PublixException resultException = (PublixException) causeCause;
			SimpleResult result = resultException.getSimpleResult(causeCause
					.getMessage());
			return Promise.<SimpleResult> pure(result);
		}
		if (cause instanceof ResultException
				|| causeCause instanceof ResultException) {
			ResultException resultException = (ResultException) causeCause
					.getCause();
			SimpleResult result = resultException.getResult();
			return Promise.<SimpleResult> pure(result);
		}
		return Promise.<SimpleResult> pure(Results
				.internalServerError(views.html.publix.error
						.render("Internal server error")));
	}

	@Override
	public Promise<SimpleResult> onHandlerNotFound(RequestHeader request) {
		return Promise.<SimpleResult> pure(Results
				.notFound(views.html.publix.error.render("Requested page \""
						+ request.uri() + "\" doesn't exist.")));
	}

	@Override
	public Promise<SimpleResult> onBadRequest(RequestHeader request,
			String error) {
		return Promise.<SimpleResult> pure(Results.badRequest("bad request"));
	}

}
