import play.GlobalSettings;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Results;
import play.mvc.SimpleResult;

import com.google.inject.Guice;
import com.google.inject.Injector;

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
