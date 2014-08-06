import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Results;
import play.mvc.SimpleResult;
import exceptions.PublixException;
import exceptions.ResultException;

public class Global extends GlobalSettings {

	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable t) {
		Throwable cause = t.getCause();
		if (cause instanceof PublixException) {
//			Logger.info(cause.getMessage());
			PublixException resultException = (PublixException) cause;
			SimpleResult result = resultException.getSimpleResult(cause
					.getMessage());
			return Promise.<SimpleResult> pure(result);
		}
		if (cause.getCause() instanceof ResultException) {
//			Logger.info(cause.getCause().getMessage());
			ResultException resultException = (ResultException) cause.getCause();
			SimpleResult result = resultException.getResult();
			return Promise.<SimpleResult> pure(result);
		}
		Logger.error(cause.getMessage());
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
