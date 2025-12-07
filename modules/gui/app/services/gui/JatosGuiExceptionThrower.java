package services.gui;

import controllers.gui.Home;
import controllers.gui.Studies;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.gui.FlashScopeMessaging;
import general.gui.RequestScopeMessaging;
import play.api.mvc.Call;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * Class with convenience methods to throw a {@link JatosGuiException}. It
 * checks whether the call is an Ajax one. It puts the error message into the
 * RequestScopeMessaging.
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosGuiExceptionThrower {

	private final Provider<Home> homeProvider;
	private final Provider<Studies> studiesProvider;

	@Inject
	JatosGuiExceptionThrower(Provider<Studies> studiesProvider,
			Provider<Home> homeProvider) {
		this.homeProvider = homeProvider;
		this.studiesProvider = studiesProvider;
	}

	/**
	 * Throws a JatosGuiException that either redirects to the given call if
	 * it's a non-Ajax request - or returns the exception's message if it's a
	 * Ajax request. The exception's type determines the response's HTTP status
	 * code.
	 */
	public void throwRedirect(Http.Request request, Exception e, Call call) throws JatosGuiException {
		Result result;
		if (Helpers.isAjax(request)) {
			int statusCode = getHttpStatusFromException(e);
			result = Results.status(statusCode, e.getMessage());
		} else {
			result = Results.redirect(call).flashing(FlashScopeMessaging.ERROR, e.getMessage());
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	/**
	 * Throws a JatosGuiException. If it's a non-Ajax request, it puts the
	 * exception's message into the request scope and returns the home view. If
	 * it's a Ajax request, it just returns the exception's message. The HTTP
	 * status code is determined by the exception type.
	 */
	public void throwHome(Http.Request request, Exception e) throws JatosGuiException {
		Result result;
		int httpStatus = getHttpStatusFromException(e);
		if (Helpers.isAjax(request)) {
			result = Results.status(httpStatus, e.getMessage());
		} else {
			RequestScopeMessaging.error(request, e.getMessage());
			result = homeProvider.get().home(request, httpStatus);
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows study's study view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwStudy(Http.Request request, String errorMsg, int httpStatus, Long studyId)
			throws JatosGuiException {
		Result result;
		if (Helpers.isAjax(request)) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(request, errorMsg);
			result = studiesProvider.get().study(request, studyId, httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException. If it's a non-Ajax request, it puts the
	 * exception's message into the request scope and returns the study's study
	 * view. If it's a Ajax request, it just returns the exception's message.
	 * The HTTP status code is determined by the exception type.
	 */
	public void throwStudy(Http.Request request, Exception e, Long studyId) throws JatosGuiException {
		Result result;
		int httpStatus = getHttpStatusFromException(e);
		if (Helpers.isAjax(request)) {
			result = Results.status(httpStatus, e.getMessage());
		} else {
			RequestScopeMessaging.error(request, e.getMessage());
			result = studiesProvider.get().study(request, studyId, httpStatus);
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	private int getHttpStatusFromException(Exception e) {
		if (e instanceof ForbiddenException) {
			return Http.Status.FORBIDDEN;
		} else if (e instanceof BadRequestException) {
			return Http.Status.BAD_REQUEST;
		} else if (e instanceof IOException) {
			return Http.Status.BAD_REQUEST;
		} else if (e instanceof NotFoundException) {
			return Http.Status.NOT_FOUND;
		} else {
			return Http.Status.INTERNAL_SERVER_ERROR;
		}
	}

}
