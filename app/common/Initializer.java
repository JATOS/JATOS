package common;

import java.io.File;
import java.util.List;
import java.util.UUID;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.JPA;
import services.UserService;

import com.google.inject.Inject;

import controllers.publix.StudyAssets;
import daos.AbstractDao;
import daos.IStudyDao;
import daos.IUserDao;

/**
 * This Initializer is called once with every start and does some JATOS specific
 * initialisation.
 * 
 * @author Kristian Lange
 */
public class Initializer {

	private static final String CLASS_NAME = Initializer.class.getSimpleName();

	private final UserService userService;
	private final IUserDao userDao;
	private final IStudyDao studyDao;

	@Inject
	public Initializer(IUserDao userDao, UserService userService,
			IStudyDao studyDao) {
		this.userDao = userDao;
		this.userService = userService;
		this.studyDao = studyDao;
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
	private void checkStudyAssetsRootDir() {
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
	private void checkUuid() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				List<StudyModel> studyModelList = studyDao.findAll();
				for (StudyModel study : studyModelList) {
					if (study.getUuid() == null || study.getUuid().isEmpty()) {
						study.setUuid(UUID.randomUUID().toString());
						AbstractDao.merge(study);
					}
					for (ComponentModel component : study.getComponentList()) {
						if (component.getUuid() == null
								|| component.getUuid().isEmpty()) {
							component.setUuid(UUID.randomUUID().toString());
							AbstractDao.merge(component);
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
