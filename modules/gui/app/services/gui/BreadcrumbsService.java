package services.gui;

import javax.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;

import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.Breadcrumbs;
import play.Logger;
import play.Logger.ALogger;

/**
 * Provides breadcrumbs for different views of JATOS' GUI.
 * 
 * @author Kristian Lange
 */
@Singleton
public class BreadcrumbsService {

	private static final ALogger LOGGER = Logger.of(BreadcrumbsService.class);

	public static final String HOME = "Home";
	public static final String WORKERS = "Workers";
	public static final String WORKER_SETUP = "Worker Setup";
	public static final String MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE = "Mechanical Turk HIT Layout Source Code";
	public static final String RESULTS = "Results";
	public static final String BATCH_MANAGER = "Batch Manager";
	public static final String USER_MANAGER = "User Manager";

	public String generateForHome() {
		return generateForHome(null);
	}

	public String generateForHome(String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		if (last != null) {
			breadcrumbs.addBreadcrumb(HOME,
					controllers.gui.routes.Home.home().url());
			breadcrumbs.addBreadcrumb(last, "");
		} else {
			breadcrumbs.addBreadcrumb(HOME, "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForHome", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public String generateForUser(User user) {
		return generateForUser(user, null);
	}

	public String generateForUser(User user, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		breadcrumbs.addBreadcrumb(HOME,
				controllers.gui.routes.Home.home().url());
		if (last != null) {
			breadcrumbs.addBreadcrumb(user.toString(),
					controllers.gui.routes.Users.profile(user.getEmail())
							.url());
			breadcrumbs.addBreadcrumb(last, "");
		} else {
			breadcrumbs.addBreadcrumb(user.toString(), "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForUser", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public String generateForStudy(Study study) {
		return generateForStudy(study, null);
	}

	public String generateForStudy(Study study, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		breadcrumbs.addBreadcrumb(HOME,
				controllers.gui.routes.Home.home().url());
		if (last != null) {
			breadcrumbs.addBreadcrumb(study.getTitle(),
					controllers.gui.routes.Studies.study(study.getId()).url());
			breadcrumbs.addBreadcrumb(last, "");
		} else {
			breadcrumbs.addBreadcrumb(study.getTitle(), "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForStudy", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public String generateForBatch(Study study, Batch batch, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		breadcrumbs.addBreadcrumb(HOME,
				controllers.gui.routes.Home.home().url());
		breadcrumbs.addBreadcrumb(study.getTitle(),
				controllers.gui.routes.Studies.study(study.getId()).url());
		breadcrumbs.addBreadcrumb(BATCH_MANAGER, controllers.gui.routes.Batches
				.batchManager(study.getId()).url());
		breadcrumbs.addBreadcrumb(batch.getTitle(), "");
		if (last != null) {
			breadcrumbs.addBreadcrumb(last, "");
		}
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForBatch", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public String generateForWorker(Worker worker) {
		return generateForWorker(worker, null);
	}

	public String generateForWorker(Worker worker, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		breadcrumbs.addBreadcrumb(HOME,
				controllers.gui.routes.Home.home().url());
		breadcrumbs.addBreadcrumb("Worker " + worker.getId(), "");
		breadcrumbs.addBreadcrumb(last, "");
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForWorker", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

	public String generateForComponent(Study study, Component component,
			String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs();
		breadcrumbs.addBreadcrumb(HOME,
				controllers.gui.routes.Home.home().url());
		breadcrumbs.addBreadcrumb(study.getTitle(),
				controllers.gui.routes.Studies.study(study.getId()).url());
		breadcrumbs.addBreadcrumb(component.getTitle(), "");
		breadcrumbs.addBreadcrumb(last, "");
		String breadcrumbsStr = "";
		try {
			breadcrumbsStr = breadcrumbs.asJson();
		} catch (JsonProcessingException e) {
			LOGGER.error(".generateForComponent", e);
			RequestScopeMessaging
					.warning(MessagesStrings.PROBLEM_GENERATING_BREADCRUMBS);
		}
		return breadcrumbsStr;
	}

}
