package common;

import java.io.File;
import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;

import models.ComponentModel;
import models.StudyModel;
import models.UserDao;
import models.UserModel;
import play.Logger;
import play.db.jpa.JPA;
import services.UserService;
import controllers.publix.StudyAssets;

/**
 * This Initializer is called once with every start and does some JATOS specific
 * initialisation.
 * 
 * @author Kristian Lange
 */
public class Initializer {

	private static final String CLASS_NAME = Initializer.class.getSimpleName();

	private UserDao userDao;
	private UserService userService;

	@Inject
	public Initializer(UserDao userDao, UserService userService) {
		this.userDao = userDao;
		this.userService = userService;
	}

	/**
	 * This method is called once with every start and does some health checks
	 * or DB updates.
	 */
	public void initialize() {
		checkAdmin();
		checkUuid();
		checkStudyAssetsRootDir();
	}

	/**
	 * Check whether studies assets root directory exists and create if not.
	 */
	private static void checkStudyAssetsRootDir() {
		boolean success = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH).mkdir();
		if (success) {
			Logger.info(CLASS_NAME
					+ ".checkStudyAssetsRootDir: Created study assets root directory "
					+ StudyAssets.STUDY_ASSETS_ROOT_PATH);
		}
	}

	/**
	 * Migration from older DB schema: generate UUID for all studies/components.
	 */
	private static void checkUuid() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				List<StudyModel> studyModelList = StudyModel.findAll();
				for (StudyModel study : studyModelList) {
					if (study.getUuid() == null || study.getUuid().isEmpty()) {
						study.setUuid(UUID.randomUUID().toString());
						study.merge();
					}
					for (ComponentModel component : study.getComponentList()) {
						if (component.getUuid() == null
								|| component.getUuid().isEmpty()) {
							component.setUuid(UUID.randomUUID().toString());
							component.merge();
						}
					}
				}
			}
		});
	}

	/**
	 * Check for user admin: In case the application is started the first time
	 * we need an initial user: admin. If admin can't be found, create one.
	 */
	private void checkAdmin() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				UserModel admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				if (admin == null) {
					userService.createAdmin();
				}
			}
		});
	}

}
