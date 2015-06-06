package common;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.persistence.Query;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
import persistance.UserDao;
import play.Logger;
import play.db.jpa.JPA;
import publix.controllers.StudyAssets;
import services.UserService;

import com.google.inject.Inject;

/**
 * This Initializer is called once with every start and does some JATOS specific
 * initialisation.
 * 
 * @author Kristian Lange
 */
public class Initializer {

	private static final String CLASS_NAME = Initializer.class.getSimpleName();

	private final UserService userService;
	private final UserDao userDao;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	Initializer(UserDao userDao, UserService userService, StudyDao studyDao,
			ComponentDao componentDao) {
		this.userDao = userDao;
		this.userService = userService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
	}

	/**
	 * This method is called once with every start and does some health checks
	 * or DB updates.
	 */
	public void initialize() {
		checkAdmin();
		checkUuid();
		checkStudyAssetsRootDir();
		checkWorkerTypes();
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
	 * Migration from older to DB schema of JATOS version 1.1.11: Change names
	 * of worker types<br>
	 * OpenStandalone -> GeneralSingle,<br>
	 * Tester -> PersonalMultiple<br>
	 * ClosedStandalone -> PersonalSingle
	 */
	private void checkWorkerTypes() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				// OpenStandalone -> GeneralSingle
				String queryStr = "UPDATE Worker SET WorkerType='GeneralSingle' WHERE WorkerType='OpenStandalone'";
				Query query = JPA.em().createQuery(queryStr);
				int count = query.executeUpdate();
				if (count > 0) {
					Logger.info(CLASS_NAME
							+ ".checkWorkerTypes: Updated "
							+ count
							+ " worker of type OpenStandalone to type GeneralSingle.");
				}
				// Tester -> PersonalMultiple
				queryStr = "UPDATE Worker SET WorkerType='PersonalMultiple' WHERE WorkerType='Tester'";
				query = JPA.em().createQuery(queryStr);
				count = query.executeUpdate();
				if (count > 0) {
					Logger.info(CLASS_NAME
							+ ".checkWorkerTypes: Updated "
							+ count
							+ " worker of type Tester to type PersonalMultiple.");
				}
				// ClosedStandalone -> PersonalSingle
				queryStr = "UPDATE Worker SET WorkerType='PersonalSingle' WHERE WorkerType='ClosedStandalone'";
				query = JPA.em().createQuery(queryStr);
				count = query.executeUpdate();
				if (count > 0) {
					Logger.info(CLASS_NAME
							+ ".checkWorkerTypes: Updated "
							+ count
							+ " worker of type ClosedStandalone to type PersonalSingle.");
				}
			}
		});
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
					if (study.getUuid() == null
							|| study.getUuid().trim().isEmpty()) {
						study.setUuid(UUID.randomUUID().toString());
					}
					Iterator<ComponentModel> iterator = study
							.getComponentList().iterator();
					while (iterator.hasNext()) {
						ComponentModel component = iterator.next();
						if (component == null) {
							iterator.remove();
						} else if (component.getUuid() == null
								|| component.getUuid().trim().isEmpty()) {
							component.setUuid(UUID.randomUUID().toString());
							componentDao.update(component);
						}
					}
					studyDao.update(study);
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
