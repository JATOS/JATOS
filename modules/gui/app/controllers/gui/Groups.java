package controllers.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import models.common.Group;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Groups extends Controller {

	private static final String CLASS_NAME = Groups.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;

	@Inject
	Groups(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			BreadcrumbsService breadcrumbsService, StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
	}

	/**
	 * Shows a view with the run manager that includes a form with group
	 * properties.
	 */
	@Transactional
	public Result runManager(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".runManager: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		Form<Group> form = Form.form(Group.class);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.RUN_MANAGER);
		return ok(views.html.gui.study.runManager.render(loggedInUser, breadcrumbs,
				form, studyId, false));
	}

	/**
	 * POST request of the form to create a new study.
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	public Result submit(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".runManager: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		return redirect(controllers.gui.routes.Studies.index(study.getId()));
	}
	
//	// Have to bind list of ALLOWED_WORKER_TYPE by hand from checkboxes
//	String[] allowedWorkerArray = Controller.request().body()
//			.asFormUrlEncoded()
//			.get(StudyProperties.ALLOWED_WORKER_TYPE_LIST);
//	if (allowedWorkerArray != null) {
//		Arrays.stream(allowedWorkerArray).forEach(
//				studyProperties::addAllowedWorkerType);
//	}

}
