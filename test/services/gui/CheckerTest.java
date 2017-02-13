package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import exceptions.gui.BadRequestException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.Study;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

/**
 * Tests Checker
 * 
 * @author Kristian Lange
 */
public class CheckerTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private Checker checker;

	@Before
	public void startApp() throws Exception {
		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	/**
	 * Test Checker.checkStandardForComponents()
	 */
	@Test
	public void checkCheckStandardForComponents()
			throws NoSuchAlgorithmException, IOException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Component component = study.getFirstComponent();

		try {
			checker.checkStandardForComponents(study.getId(), component.getId(),
					component);
		} catch (BadRequestException e) {
			Fail.fail();
		}

		long nonExistentStudyId = 2l;
		try {
			checker.checkStandardForComponents(nonExistentStudyId,
					component.getId(), component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentNotBelongToStudy(
							nonExistentStudyId, component.getId()));
		}

		component.setStudy(null);
		try {
			checker.checkStandardForComponents(study.getId(), component.getId(),
					component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentHasNoStudy(component.getId()));
		}

		component = null;
		try {
			checker.checkStandardForComponents(study.getId(), null, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentNotExist(null));
		}
	}

}
