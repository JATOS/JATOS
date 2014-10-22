package controllers.publix;

import models.ComponentModel;
import models.workers.Worker;
import play.Logger;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;

import controllers.Users;
import exceptions.PublixException;

/**
 * Abstract controller class for all controller that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
public abstract class Publix extends Controller implements
		IPublix {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	protected PublixUtils<? extends Worker> utils;

	public Publix(PublixUtils<? extends Worker> utils) {
		this.utils = utils;
	}

	@Override
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		Logger.info(CLASS_NAME + ".startComponentByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		ComponentModel component = utils.retrieveComponentByPosition(studyId,
				position);
		return startComponent(studyId, component.getId());
	}

	@Override
	public Result getComponentDataByPosition(Long studyId, Integer position)
			throws PublixException, JsonProcessingException {
		Logger.info(CLASS_NAME + ".getComponentDataByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		ComponentModel component = utils.retrieveComponentByPosition(studyId,
				position, MediaType.TEXT_JAVASCRIPT_UTF_8);
		return getComponentData(studyId, component.getId());
	}

	@Override
	public Result submitResultDataByPosition(Long studyId, Integer position)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultDataByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		ComponentModel component = utils.retrieveComponentByPosition(studyId,
				position, MediaType.TEXT_JAVASCRIPT_UTF_8);
		return submitResultData(studyId, component.getId());
	}

	@Override
	public Result logError() {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " - logging client-side error: " + msg);
		return ok();
	}

	@Override
	public Result teapot() {
		return status(418, "I'm a teapot");
	}

	public static Promise<Result> forwardTo(String url) {
		Promise<WS.Response> response = WS.url(url).get();
		return response.map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				return ok(response.getBody()).as("text/html");
			}
		});
	}

}
