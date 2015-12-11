package controllers.gui;

import java.util.Arrays;

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
import models.gui.GroupProperties;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.GroupService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;

/**
 * Controller for all actions regarding groups and runs within the JATOS GUI.
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
	private final GroupService groupService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;

	@Inject
	Groups(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			GroupService groupService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.groupService = groupService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
	}

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

		Group group = study.getGroupList().get(0);
		GroupProperties groupProperties = groupService
				.bindToGroupProperties(group);
		Form<GroupProperties> form = Form.form(GroupProperties.class)
				.fill(groupProperties);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.RUN_MANAGER);
		return ok(views.html.gui.study.runManager.render(loggedInUser,
				breadcrumbs, group.getId(), form, studyId, study.isLocked()));
	}

	/**
	 * Ajax POST request
	 */
	@Transactional
	public Result submitProperties(Long studyId, Long groupId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitProperties: studyId " + studyId
				+ ", groupId " + groupId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Group currentGroup = study.getGroupList().get(0);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}

		Form<GroupProperties> form = Form.form(GroupProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		GroupProperties groupProperties = form.get();
		// Have to bind ALLOWED_WORKER_TYPES by hand from checkboxes
		String[] allowedWorkerArray = Controller.request().body()
				.asFormUrlEncoded().get(GroupProperties.ALLOWED_WORKER_TYPES);
		if (allowedWorkerArray != null) {
			Arrays.stream(allowedWorkerArray)
					.forEach(groupProperties::addAllowedWorkerType);
		}

		Group updatedGroup = groupService.bindToGroup(groupProperties);
		groupService.updateGroup(currentGroup, updatedGroup);
		return ok();
	}

}
