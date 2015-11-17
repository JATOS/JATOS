package general;

import java.io.File;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.GroupResultDao;
import daos.common.UserDao;
import general.common.Common;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import models.common.User;
import play.Logger;
import play.db.jpa.JPAApi;
import services.gui.UserService;

/**
 * This Initializer is called once with every start of JATOS and does some JATOS
 * specific initialisation.
 * 
 * @author Kristian Lange
 */
@Singleton
public class Initializer {

	private static final String CLASS_NAME = Initializer.class.getSimpleName();

	private final JPAApi jpa;
	private final Common common;
	private final UserService userService;
	private final UserDao userDao;
	private final GroupResultDao groupResultDao;

	@Inject
	Initializer(JPAApi jpa, Common common, UserDao userDao, UserService userService, GroupResultDao groupResultDao) {
		this.jpa = jpa;
		this.common = common;
		this.userDao = userDao;
		this.userService = userService;
		this.groupResultDao = groupResultDao;
		initialize();
	}

	/**
	 * This method is called once with every start and does some health checks
	 * or DB updates.
	 */
	public void initialize() {
		checkAdmin();
		checkStudyAssetsRootDir();
		checkGroupResults();
		Logger.info(CLASS_NAME + ": JATOS initialized");
	}

	/**
	 * Check whether studies assets root directory exists and create if not.
	 */
	private void checkStudyAssetsRootDir() {
		boolean success = new File(common.getStudyAssetsRootPath()).mkdir();
		if (success) {
			Logger.info(CLASS_NAME + ".checkStudyAssetsRootDir: Created study assets root directory "
					+ common.getStudyAssetsRootPath());
		}
	}

	/**
	 * Check for user admin: In case the application is started the first time
	 * we need an initial user: admin. If admin can't be found, create one.
	 */
	private void checkAdmin() {
		jpa.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			if (admin == null) {
				userService.createAdmin();
			}
		});
	}

	/**
	 * Check that all group results are in state FINISHED
	 */
	private void checkGroupResults() {
		jpa.withTransaction(() -> {
			List<GroupResult> groupResultList = groupResultDao.findAllNotFinished();
			for (GroupResult groupresult : groupResultList) {
				groupresult.setGroupState(GroupState.FINISHED);
				groupResultDao.update(groupresult);
				Logger.info(CLASS_NAME + ".checkGroupResults: All group results should be "
						+ "finished when starting, but group result " + groupresult.getId()
						+ " wasn't. Finish it now.");
			}
		});
	}
}
