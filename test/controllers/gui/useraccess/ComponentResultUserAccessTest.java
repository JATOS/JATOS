package controllers.gui.useraccess;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.UserDao;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;
import services.gui.UserService;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

/**
 * Testing controller actions of ComponentResults whether they have proper
 * access control: only the right user should be allowed to do the action.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class ComponentResultUserAccessTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private UserDao userDao;

	@Inject
	private ResultCreator resultCreator;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

	@Inject
	private UserAccessTestHelpers userAccessTestHelpers;

	@Before
	public void startApp() throws Exception {
		fakeApplication = Helpers.fakeApplication();

		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);

		Helpers.start(fakeApplication);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void callComponentResults() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.componentResults(study.getId(), study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		StudyResult studyResult = createTwoComponentResults(study);
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(0);
		Call call = controllers.gui.routes.ComponentResults
				.remove(componentResult.getId().toString());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);

		// Logged-in user must be an user of the study to which the
		// ComponentResult belongs that is to be deleted - if not an HTTP 403 is
		// expected
		User someUser = testHelper.createAndPersistUser("bla@bla.com", "Bla",
				"bla");
		userAccessTestHelpers.checkThatCallIsForbidden(call, Helpers.DELETE,
				someUser);
		testHelper.removeUser("bla@bla.com");
	}

	private StudyResult createTwoComponentResults(Study study) {
		return jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			StudyResult studyResult = resultCreator.createStudyResult(study,
					study.getDefaultBatch(), admin.getWorker());
			try {
				jatosPublixUtils.startComponent(study.getFirstComponent(),
						studyResult);
				jatosPublixUtils.startComponent(study.getFirstComponent(),
						studyResult);
			} catch (ForbiddenReloadException e) {
				e.printStackTrace();
			}
			return studyResult;
		});
	}

	@Test
	public void callComponentResultsRemoveAllOfComponent() throws IOException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.removeAllOfComponent(study.getId(),
						study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.DELETE);
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.tableDataByComponent(study.getId(),
						study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

}
