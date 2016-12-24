package services.publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;

import exceptions.publix.ForbiddenReloadException;
import general.AbstractTest;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
import services.publix.workers.JatosPublixUtils;

/**
 * Tests for class PublixHelpers
 * 
 * @author Kristian Lange
 */
public class PublixHelpersTest extends AbstractTest {

	// The worker is not important here
	protected PublixUtils<JatosWorker> publixUtils;

	@Override
	public void before() throws Exception {
		publixUtils = application.injector().instanceOf(JatosPublixUtils.class);
	}

	@Override
	public void after() throws Exception {
	}

	/**
	 * Test PublixUtils.finishedStudyAlready(): check for all different states
	 * of a StudyResult
	 */
	@Test
	public void checkFinishedStudyAlready() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult = addStudyResult(study, admin.getWorker());

		// Study results in state FINISHED, ABORTED, or FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();

		// Study results in state PRE, STARTED, or DATA_RETRIEVED must return
		// false
		studyResult.setStudyState(StudyState.PRE);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.didStudyAlready(): normal functioning
	 */
	@Test
	public void checkDidStudyAlready() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Create a result for the admin's worker
		addStudyResult(study, admin.getWorker());

		assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study))
				.isTrue();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixHelpers.studyDone() for the different study result states
	 */
	@Test
	public void checkStudyDone() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult = addStudyResult(study, admin.getWorker());

		// FINISHED, ABORTED, FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();

		// DATA_RETRIEVED, STARTED must return false
		studyResult.setStudyState(StudyState.PRE);
		assertThat(PublixHelpers.studyDone(studyResult)).isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(PublixHelpers.studyDone(studyResult)).isFalse();
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(PublixHelpers.studyDone(studyResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixHelpers.componentDone() for all the different component
	 * result states
	 */
	@Test
	public void checkComponentDone()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		// Create a study result and start a component to get a component result
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		// Have to set study manually in test - don't know why
		componentResult.getComponent().setStudy(study);
		entityManager.getTransaction().commit();

		// A component is done if state FINISHED, ABORTED, FAIL, or RELOADED
		componentResult.setComponentState(ComponentState.FINISHED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.ABORTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.FAIL);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.RELOADED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();

		// Not done if
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.STARTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

}
