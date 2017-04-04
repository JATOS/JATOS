package controllers.gui.useraccess;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Study;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

/**
 * Testing controller actions of Components whether they have proper access
 * control: only the right user should be allowed to do the action.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class ComponentsUserAccessTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

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
	public void callComponentsRunComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.runComponent(
				study.getId(), study.getComponent(1).getId(), -1l);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callComponentsSubmitCreated() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.submitCreated(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callComponentsProperties() throws IOException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.properties(study.getId(),
				study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callComponentsSubmitEdited() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.submitEdited(study.getId(), study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callComponentsToggleActive() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.toggleActive(
				study.getId(), study.getComponent(1).getId(), true);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.cloneComponent(study.getId(), study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.remove(study.getId(),
				study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.DELETE);
	}

}
