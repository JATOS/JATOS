package controllers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.api.mvc.Call;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.JsonUtils.UploadUnmarshaller;
import services.Messages;
import services.PersistanceUtils;
import services.ZipUtil;
import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Studies extends Controller {

	private static final String CLASS_NAME = Studies.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Set<Worker> workerSet = ControllerUtils.retrieveWorkers(study);
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study);
		return status(httpStatus, views.html.mecharg.study.index.render(
				studyList, loggedInUser, breadcrumbs, messages, study,
				workerSet));
	}

	@Transactional
	public static Result index(Long studyId, String errorMsg)
			throws ResultException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result index(Long studyId) throws ResultException {
		return index(studyId, null, Http.Status.OK);
	}

	@Transactional
	public static Result create() throws ResultException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());

		Form<StudyModel> form = Form.form(StudyModel.class);
		Call submitAction = routes.Studies.submit();
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome("New Study");
		return ok(views.html.mecharg.study.edit.render(studyList, loggedInUser,
				breadcrumbs, null, submitAction, form, false));
	}

	@Transactional
	public static Result submit() throws ResultException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		if (form.hasErrors()) {
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome("New Study");
			Call submitAction = routes.Studies.submit();
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, false);
		}

		// Persist in DB
		StudyModel study = form.get();
		PersistanceUtils.addStudy(study, loggedInUser);

		// Create study's dir
		try {
			IOUtils.createStudyDir(study);
		} catch (IOException e) {
			form.reject(e.getMessage());
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome("New Study");
			Call submitAction = routes.Studies.submit();
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, false);
		}
		return redirect(routes.Studies.index(study.getId(), null));
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result importStudy() throws ResultException {
		Logger.info(CLASS_NAME + ".importStudy: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		MultipartFormData mfd = request().body().asMultipartFormData();
		List<FilePart> filePartList = mfd.getFiles();
		for (FilePart filePart : filePartList) {
			// If the key isn't right the upload doesn't come from the right
			// form
			if (!filePart.getKey().equals(StudyModel.STUDY)) {
				String errorMsg = ErrorMessages.NO_STUDY_UPLOAD;
				ControllerUtils.throwHomeResultException(errorMsg,
						Http.Status.BAD_REQUEST);
			}
			File tempDir = unzipUploadedFile(filePart);
			StudyModel study = unmarshalStudy(tempDir);
			PersistanceUtils.addStudy(study, loggedInUser);
			moveStudyDir(tempDir, study);
		}
		return ok();
	}

	private static void moveStudyDir(File tempDir, StudyModel study)
			throws ResultException {
		try {
			File[] dirArray = IOUtils.findDirectories(tempDir);
			if (dirArray.length == 0) {
				// If a study dir is missing, create a new one.
				IOUtils.createStudyDir(study);
				// TODO send warning message
			} else if (dirArray.length == 1) {
				File studyDir = dirArray[0];
				IOUtils.moveStudyDirectory(studyDir, study);
			} else {
				// More than one dir is forbidden
				String errorMsg = ErrorMessages.MORE_THAN_ONE_DIR_IN_ZIP;
				ControllerUtils.throwHomeResultException(errorMsg,
						Http.Status.BAD_REQUEST);
			}
		} catch (IOException e) {
			String errorMsg = "Study not imported: " + e.getMessage();
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

	private static StudyModel unmarshalStudy(File tempDir)
			throws ResultException {
		File studyFile = IOUtils.findFiles(tempDir, "",
				IOUtils.STUDY_FILE_SUFFIX)[0];
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		StudyModel study = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		if (study == null) {
			ControllerUtils.throwHomeResultException(
					uploadUnmarshaller.getErrorMsg(), Http.Status.BAD_REQUEST);
		}
		if (study.validate() != null) {
			String errorMsg = ErrorMessages.COMPONENT_INVALID;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		studyFile.delete();
		return study;
	}

	private static File unzipUploadedFile(FilePart filePart)
			throws ResultException {
		File tempDir = null;
		try {
			tempDir = ZipUtil.unzip(filePart.getFile());
		} catch (IOException e1) {
			String errorMsg = ErrorMessages.IMPORT_OF_STUDY_FAILED;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return tempDir;
	}

	@Transactional
	public static Result edit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages();
		if (study.isLocked()) {
			messages.warning(ErrorMessages.STUDY_IS_LOCKED);
		}
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		Call submitAction = routes.Studies.submitEdited(study.getId());
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study, "Edit");
		return ok(views.html.mecharg.study.edit.render(studyList, loggedInUser,
				breadcrumbs, messages, submitAction, form, study.isLocked()));
	}

	@Transactional
	public static Result submitEdited(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Studies.submitEdited(study.getId());
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					"Edit");
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study.isLocked());
		}

		// Update study in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(StudyModel.TITLE);
		String description = requestData.get(StudyModel.DESCRIPTION);
		String dirName = requestData.get(StudyModel.DIRNAME);
		String jsonData = requestData.get(StudyModel.JSON_DATA);
		String oldDirName = study.getDirName();
		PersistanceUtils.updateStudy(study, title, description, dirName,
				jsonData);
		try {
			IOUtils.renameStudyDir(oldDirName, study.getDirName(),
					study.getId());
		} catch (IOException e) {
			form.reject(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			Call submitAction = routes.Studies.submitEdited(study.getId());
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					"Edit");
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study.isLocked());
		}
		return redirect(routes.Studies.index(studyId, null));
	}

	/**
	 * Ajax POST request to swap the locked field.
	 */
	@Transactional
	public static Result swapLock(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".swapLock: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		study.setLocked(!study.isLocked());
		study.merge();
		return ok(String.valueOf(study.isLocked()));
	}

	/**
	 * Ajax DELETE request to remove a study
	 */
	@Transactional
	public static Result remove(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		PersistanceUtils.removeStudy(study);

		// Remove study's dir
		try {
			IOUtils.removeStudyDirectory(study);
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok();
	}

	/**
	 * Ajax POST request
	 */
	@Transactional
	public static Result cloneStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".cloneStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		StudyModel clone = new StudyModel(study);
		clone.addMember(loggedInUser);

		// Copy study's dir and it's content to cloned study's dir
		try {
			IOUtils.copyStudyDirectory(study, clone);
		} catch (IOException e) {
			ControllerUtils.throwAjaxResultException(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		clone.persist();
		return ok();
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result exportStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".exportStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		File zipFile = null;
		try {
			File studyAsJsonFile = File.createTempFile(
					IOUtils.generateFileName(study.getTitle()), "."
							+ IOUtils.STUDY_FILE_SUFFIX);
			JsonUtils.asJsonForIO(study, studyAsJsonFile);
			String studyDirPath = IOUtils.generateStudysPath(study);
			zipFile = ZipUtil.zipStudy(studyDirPath, study.getDirName(),
					studyAsJsonFile.getAbsolutePath());
			studyAsJsonFile.delete();
		} catch (IOException e) {
			String errorMsg = ErrorMessages.studyExportFailure(studyId);
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		String zipFileName = IOUtils.generateFileName(study.getTitle(),
				IOUtils.ZIP_FILE_SUFFIX);
		response().setContentType("application/x-download");
		response().setHeader("Content-disposition",
				"attachment; filename=" + zipFileName);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(zipFile);
	}

	@Transactional
	public static Result changeMembers(Long studyId) throws ResultException {
		return changeMembers(studyId, null, Http.Status.OK);
	}

	@Transactional
	public static Result changeMembers(Long studyId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".changeMembers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		List<UserModel> userList = UserModel.findAll();
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				"Change Members");
		return status(httpStatus,
				views.html.mecharg.study.changeMembers.render(studyList,
						loggedInUser, breadcrumbs, messages, study, userList));
	}

	@Transactional
	public static Result submitChangedMembers(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(StudyModel.MEMBERS);
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = ErrorMessages.STUDY_AT_LEAST_ONE_MEMBER;
			ControllerUtils.throwChangeMemberOfStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			UserModel user = UserModel.findByEmail(email);
			if (user != null) {
				PersistanceUtils.addMemberToStudy(study, user);
			}
		}
		return redirect(routes.Studies.index(studyId, null));
	}

	/**
	 * Ajax POST request to change the oder of components within an study.
	 */
	@Transactional
	public static Result changeComponentOrder(Long studyId, Long componentId,
			String direction) throws ResultException {
		Logger.info(CLASS_NAME + ".changeComponentOrder: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		if (direction.equals("up")) {
			study.componentOrderMinusOne(component);
		}
		if (direction.equals("down")) {
			study.componentOrderPlusOne(component);
		}
		study.refresh();

		return ok();
	}

	@Transactional
	public static Result showStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".showStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		session(MAPublix.MECHARG_SHOW, MAPublix.SHOW_STUDY);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startStudy(study.getId()));
	}

	@Transactional
	public static Result showMTurkSourceCode(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".showMTurkSourceCode: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		String[] referer = request().headers().get("Referer");
		URL mechArgURL = new URL(referer[0]);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				"Mechanical Turk HIT Layout Source Code");
		return ok(views.html.mecharg.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, mechArgURL));
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allComponentsForUI(study.getComponentList());
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	@Transactional
	public static Result workers(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".workers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForStudy(study, "Workers");
		return status(httpStatus,
				views.html.mecharg.study.studysWorkers.render(studyList,
						loggedInUser, breadcrumbs, messages, study));
	}

	@Transactional
	public static Result workers(Long studyId, String errorMsg)
			throws ResultException {
		return workers(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result workers(Long studyId) throws ResultException {
		return workers(studyId, null, Http.Status.OK);
	}

}
