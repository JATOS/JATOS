import java.lang.reflect.Method;

import com.google.inject.Guice;
import com.google.inject.Injector;

import play.GlobalSettings;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.Results;
import play.mvc.SimpleResult;
import controllers.publix.MAPublix;
import controllers.publix.MTPublix;
import exceptions.PublixException;
import exceptions.ResultException;

public class Global extends GlobalSettings {

	private static final Injector INJECTOR = createInjector();

	@Override
	public <A> A getControllerInstance(Class<A> controllerClass)
			throws Exception {
		return INJECTOR.getInstance(controllerClass);
	}

	private static Injector createInjector() {
		return Guice.createInjector();
	}

	@Override
	public Action onRequest(Request request, final Method actionMethod) {
		// Check if this is a request for the Publix class but originates from
		// within MechArg and wants to try a component or study
		String playCookie = request.cookie("PLAY_SESSION").value();
		boolean isFromMechArg = playCookie.contains(MAPublix.MECHARG_TRY);
		boolean isForPublix = actionMethod.getDeclaringClass().equals(
				MTPublix.class);
		if (isForPublix && isFromMechArg) {
			return redirectToMAPublix(actionMethod);
		}
		return super.onRequest(request, actionMethod);
	}

	/**
	 * Creates an action that that redirects to MAPublix (uri prefix
	 * '/mecharg').
	 */
	private Action redirectToMAPublix(final Method actionMethod) {
		return new Action.Simple() {
			@Override
			public Promise<SimpleResult> call(play.mvc.Http.Context ctx)
					throws Throwable {
				String uri = "/mecharg" + ctx.request().uri();
				SimpleResult result = redirect(uri);
				return Promise.<SimpleResult> pure(result);
			}
		};
	}

	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable t) {
		Throwable cause = t.getCause();
		Throwable causeCause = t.getCause().getCause();
		if (cause instanceof PublixException) {
			PublixException publixException = (PublixException) cause;
			SimpleResult result = publixException
					.getSimpleResult(publixException.getMessage());
			return Promise.<SimpleResult> pure(result);
		}
		if (causeCause instanceof PublixException) {
			PublixException publixException = (PublixException) causeCause;
			SimpleResult result = publixException
					.getSimpleResult(publixException.getMessage());
			return Promise.<SimpleResult> pure(result);
		}
		if (cause instanceof ResultException) {
			ResultException resultException = (ResultException) cause;
			SimpleResult result = resultException.getResult();
			return Promise.<SimpleResult> pure(result);
		}
		if (causeCause instanceof ResultException) {
			ResultException resultException = (ResultException) causeCause;
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
