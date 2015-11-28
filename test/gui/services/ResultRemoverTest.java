package gui.services;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import exceptions.publix.ForbiddenReloadException;
import general.AbstractTest;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import services.gui.ResultRemover;
import services.publix.workers.JatosPublixUtils;

/**
 * Tests ResultRemover
 * 
 * @author Kristian Lange
 */
public class ResultRemoverTest extends AbstractTest {

	private ResultRemover resultRemover;
	private JatosPublixUtils jatosPublixUtils;

	@Override
	public void before() throws Exception {
		resultRemover = application.injector().instanceOf(ResultRemover.class);
		jatosPublixUtils = application.injector()
				.instanceOf(JatosPublixUtils.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkRemoveComponentResults()
			throws IOException, BadRequestException, NotFoundException,
			ForbiddenException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		// Check that we have 2 results
		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(idList);
		assertThat(componentResultList.size()).isEqualTo(2);

		// Now remove them
		entityManager.getTransaction().begin();
		resultRemover.removeComponentResults("1, 2", admin);
		entityManager.getTransaction().commit();

		// Check that the results are removed
		try {
			idList = resultService.extractResultIds("1, 2");
			componentResultList = resultService.getComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentResultNotExist(1l));
		}

		// Clean-up
		removeStudy(study);
	}

	private void createTwoComponentResults(Study study)
			throws ForbiddenReloadException {
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		study.getFirstComponent().setStudy(study);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();
	}

	@Test
	public void checkRemoveComponentResultsNotFound()
			throws IOException, BadRequestException, ForbiddenException,
			NotFoundException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		// Check that we have 2 results
		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(idList);
		assertThat(componentResultList.size()).isEqualTo(2);

		// Now try to remove the results 1 and 3 (3 doesn't exist)
		try {
			resultRemover.removeComponentResults("1, 3", admin);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentResultNotExist(3l));
		}

		// Check that NO result is removed - not even result 1
		idList = resultService.extractResultIds("1, 2");
		componentResultList = resultService.getComponentResults(idList);
		assertThat(componentResultList.size()).isEqualTo(2);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRemoveStudyResults()
			throws IOException, BadRequestException, NotFoundException,
			ForbiddenException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Check that we have 2 results
		List<Long> idList = resultService.extractResultIds("1, 2");
		List<StudyResult> studyResultList = resultService
				.getStudyResults(idList);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Now remove them
		entityManager.getTransaction().begin();
		resultRemover.removeStudyResults("1, 2", admin);
		entityManager.getTransaction().commit();

		studyResultList = studyResultDao.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(0);

		// Clean-up
		removeStudy(study);
	}

	private void createTwoStudyResults(Study study)
			throws ForbiddenReloadException {
		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		ComponentResult componentResult11 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult11.setData(
				"First ComponentResult's data of the first StudyResult.");
		ComponentResult componentResult12 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult12.setData(
				"Second ComponentResult's data of the first StudyResult.");

		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		ComponentResult componentResult21 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult21.setData(
				"First ComponentResult's data of the second StudyResult.");
		ComponentResult componentResult22 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult22.setData(
				"Second ComponentResult's data of the second StudyResult.");
		entityManager.getTransaction().commit();
	}

	@Test
	public void checkRemoveAllStudyResults() throws IOException,
			ForbiddenException, BadRequestException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Check that we have 2 results
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Remove them
		entityManager.getTransaction().begin();
		resultRemover.removeAllStudyResults(study, admin);
		entityManager.getTransaction().commit();

		// Check that we have no more results
		studyResultList = studyResultDao.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(0);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRemoveAllStudyResultsWrongUser()
			throws IOException, BadRequestException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Check that we have 2 results
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(2);

		// And now try to remove them with the wrong user
		User testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		try {
			resultRemover.removeAllStudyResults(study, testUser);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(MessagesStrings.studyNotUser(
					testUser.getName(), testUser.getEmail(), study.getId(),
					study.getTitle()));
		}

		// Check that we still have 2 results
		studyResultList = studyResultDao.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRemoveAllStudyResultsStudyLocked()
			throws BadRequestException, IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Check that we have 2 results
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Lock study
		entityManager.getTransaction().begin();
		study.setLocked(true);
		entityManager.getTransaction().commit();

		try {
			resultRemover.removeAllStudyResults(study, admin);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.studyLocked(study.getId()));
		}

		// Check that we still have 2 results
		studyResultList = studyResultDao.findAllByStudy(study);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Clean-up
		removeStudy(study);
	}

}
