package exceptions.gui;


import models.gui.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.NOT_FOUND;

public class NotFoundException extends HttpException {

	public NotFoundException(String message) {
		super(NOT_FOUND, message, ErrorCode.NOT_FOUND);
	}

	public NotFoundException(String message, ErrorCode errorCode) {
		super(NOT_FOUND, message, errorCode);
	}

}
