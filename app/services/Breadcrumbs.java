package services;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import common.RequestScopeMessaging;
import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.mvc.Call;
import utils.JsonUtils;

/**
 * Provides breadcrumbs for different views of JATOS' GUI.
 * 
 * @author Kristian Lange
 */
public class Breadcrumbs {

	private static final String CLASS_NAME = Breadcrumbs.class.getSimpleName();
	
	public static final String HOME = "Home";
	public static final String EDIT_PROPERTIES = "Edit Properties";
	public static final String WORKERS = "Workers";
	public static final String MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE = "Mechanical Turk HIT Layout Source Code";
	public static final String CHANGE_MEMBERS = "Change Members";
	public static final String NEW_STUDY = "New Study";
	public static final String RESULTS = "Results";
	public static final String NEW_COMPONENT = "New Component";
	public static final String CHANGE_PASSWORD = "Change Password";
	public static final String EDIT_PROFILE = "Edit Profile";
	public static final String NEW_USER = "New User";

	private final Map<String, String> breadcrumbs = new LinkedHashMap<>();

	public Map<String, String> getBreadcrumbs() {
		return breadcrumbs;
	}

	private Breadcrumbs put(String name, String url) {
		this.breadcrumbs.put(name, url);
		return this;
	}

	private Breadcrumbs put(String name, Call call) {
		this.breadcrumbs.put(name, call.url());
		return this;
	}

	public static String generateForHome() {
		return generateForHome(null);
	}

	public static String generateForHome(String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		if (last != null) {
			breadcrumbs = new Breadcrumbs().put(HOME,
					controllers.routes.Home.home()).put(last, "");
		} else {
			breadcrumbs.put(HOME, "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = JsonUtils.asJson(breadcrumbs);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".generateForHome", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public static String generateForUser(UserModel user) {
		return generateForUser(user, null);
	}

	public static String generateForUser(UserModel user, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs().put(HOME,
				controllers.routes.Home.home());
		if (last != null) {
			breadcrumbs.put(user.toString(),
					controllers.routes.Users.profile(user.getEmail())).put(
					last, "");
		} else {
			breadcrumbs.put(user.toString(), "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = JsonUtils.asJson(breadcrumbs);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".generateForUser", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public static String generateForStudy(StudyModel study) {
		return generateForStudy(study, null);
	}

	public static String generateForStudy(StudyModel study, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs().put(HOME,
				controllers.routes.Home.home());
		if (last != null) {
			breadcrumbs.put(study.getTitle(),
					controllers.routes.Studies.index(study.getId())).put(last,
					"");
		} else {
			breadcrumbs.put(study.getTitle(), "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = JsonUtils.asJson(breadcrumbs);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".generateForStudy", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public static String generateForWorker(Worker worker) {
		return generateForWorker(worker, null);
	}

	public static String generateForWorker(Worker worker, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs()
				.put(HOME, controllers.routes.Home.home())
				.put("Worker " + worker.getId(), "").put(last, "");
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = JsonUtils.asJson(breadcrumbs);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".generateForWorker", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public static String generateForComponent(StudyModel study,
			ComponentModel component, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs()
				.put(HOME, controllers.routes.Home.home())
				.put(study.getTitle(),
						controllers.routes.Studies.index(study.getId()))
				.put(component.getTitle(), "").put(last, "");
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = JsonUtils.asJson(breadcrumbs);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".generateForComponent", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

}
