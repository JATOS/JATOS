package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

import play.mvc.Results;
import play.mvc.SimpleResult;

@SuppressWarnings("serial")
public class PublixException extends Exception {

	protected MediaType mediaType = MediaType.HTML_UTF_8;
	protected int httpStatus = HttpStatus.SC_BAD_REQUEST;;
	
	public PublixException(String message) {
		super(message);
	}
	
	public PublixException(String message, MediaType mediaType, int httpStatus) {
		super(message);
		this.mediaType = mediaType;
		this.httpStatus = httpStatus;
	}
	
	public PublixException(String message, MediaType mediaType) {
		super(message);
		this.mediaType = mediaType;
	}
	
	public PublixException(String message, int httpStatus) {
		super(message);
		this.httpStatus = httpStatus;
	}

	public SimpleResult getSimpleResult(String message) {
		if (mediaType == MediaType.HTML_UTF_8) {
			switch (httpStatus) {
			case HttpStatus.SC_BAD_REQUEST:
				return Results.badRequest(views.html.publix.error
						.render(message));
			case HttpStatus.SC_FORBIDDEN:
				return Results.forbidden(views.html.publix.error
						.render(message));
			default:
				return Results.internalServerError(views.html.publix.error
						.render(message));
			}
		}
		
		switch (httpStatus) {
		case HttpStatus.SC_BAD_REQUEST:
			return Results.badRequest(message);
		case HttpStatus.SC_FORBIDDEN:
			return Results.forbidden(message);
		default:
			return Results.internalServerError(message);
		}
	}

}
