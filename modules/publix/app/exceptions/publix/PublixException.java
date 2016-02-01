package exceptions.publix;

import org.apache.http.HttpStatus;

import play.mvc.Result;
import play.mvc.Results;
import utils.common.ControllerUtils;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public abstract class PublixException extends Exception {

	protected MediaType mediaType = MediaType.HTML_UTF_8;
	protected int httpStatus = HttpStatus.SC_BAD_REQUEST;

	public PublixException(String message) {
		super(message);
	}

	public PublixException(String message, int httpStatus) {
		super(message);
		this.httpStatus = httpStatus;
	}

	public Result getSimpleResult() {
		if (!ControllerUtils.isAjax()) {
			return Results.status(httpStatus,
					views.html.publix.error.render(getMessage()));
		} else {
			return Results.status(httpStatus, getMessage());
		}
	}

}
