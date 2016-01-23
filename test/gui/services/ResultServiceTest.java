package gui.services;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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
import services.publix.workers.JatosPublixUtils;

/**
 * Tests ResultService
 * 
 * @author Kristian Lange
 */
public class ResultServiceTest extends AbstractTest {

	private JatosPublixUtils jatosPublixUtils;

	@Override
	public void before() throws Exception {
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
	public void checkExtractResultIds() {
		List<Long> resultIdList = null;

		// Valid ID string
		try {
			resultIdList = resultService.extractResultIds("1,2,3");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		// Still valid, but with weird whitespaces and empty fields
		try {
			resultIdList = resultService
					.extractResultIds(" , ,, 1 ,2    ,  3    , ");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		// Not valid due to letter instead of number
		try {
			resultIdList = resultService.extractResultIds("1,b,3");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.resultIdMalformed("b"));
		}

		// Not valid due to empty
		try {
			resultIdList = resultService.extractResultIds("");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.NO_RESULTS_SELECTED);
		}
	}

	private void checkForProperResultIdList(List<Long> resultIdList) {
		assertThat(resultIdList.size() == 3);
		assertThat(resultIdList.contains(1l));
		assertThat(resultIdList.contains(2l));
		assertThat(resultIdList.contains(3l));
	}

	@Test
	public void checkCheckComponentResults()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(idList);

		// Must not throw an exception
		checker.checkComponentResults(componentResultList, admin, true);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckComponentResultsWrongUser()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(idList);

		// Check results with wrong user
		User testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		try {
			checker.checkComponentResults(componentResultList, testUser, true);
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(MessagesStrings.studyNotUser(
					testUser.getName(), testUser.getEmail(), study.getId(),
					study.getTitle()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckComponentResultsLocked()
			throws IOException, BadRequestException, NotFoundException,
			ForbiddenException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(idList);

		// Lock study
		entityManager.getTransaction().begin();
		componentResultList.get(0).getComponent().getStudy().setLocked(true);
		entityManager.getTransaction().commit();

		// Must not throw an exception since we tell it not to check for locked
		// study
		checker.checkComponentResults(componentResultList, admin, false);

		// Must throw an exception since we told it to check for locked study
		try {
			checker.checkComponentResults(componentResultList, admin, true);
		} catch (ForbiddenException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.studyLocked(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckStudyResults()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<StudyResult> studyResultList = resultService
				.getStudyResults(idList);

		// Must not throw an exception
		checker.checkStudyResults(studyResultList, admin, true);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckStudyResultsLocked()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<StudyResult> studyResultList = resultService
				.getStudyResults(idList);

		// Lock study
		entityManager.getTransaction().begin();
		studyResultList.get(0).getStudy().setLocked(true);
		entityManager.getTransaction().commit();

		// Must not throw an exception since we tell it not to check for locked
		// study
		checker.checkStudyResults(studyResultList, admin, false);

		// Must throw an exception since we told it to check for locked study
		try {
			checker.checkStudyResults(studyResultList, admin, true);
		} catch (ForbiddenException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.studyLocked(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetComponentResults() throws IOException,
			BadRequestException, NotFoundException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		// Check that we can get ComponentResults
		List<Long> idList = resultService.extractResultIds("1, 2");
		List<ComponentResult> componentResultList = null;
		componentResultList = resultService.getComponentResults(idList);
		assertThat(componentResultList.size()).isEqualTo(2);

		// Clean-up
		removeStudy(study);
	}

	private void createTwoComponentResults(Study study)
			throws ForbiddenReloadException {
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		study.getFirstComponent().setStudy(study);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();
	}

	@Test
	public void checkGetComponentResultsWrongId()
			throws IOException, BadRequestException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoComponentResults(study);

		// If one of the IDs don't exist it throws an exception
		List<Long> idList = resultService.extractResultIds("1, 2, 9, 10");
		try {
			resultService.getComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentResultNotExist(9l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetComponentResultsNotExist()
			throws BadRequestException, NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		// Check that ComponentResults with ID 1, 2 don't exist
		List<Long> idList = resultService.extractResultIds("1, 2");
		try {
			resultService.getComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentResultNotExist(1l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetStudyResults() throws IOException,
			NoSuchAlgorithmException, BadRequestException, NotFoundException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<StudyResult> studyResultList = resultService
				.getStudyResults(idList);
		assertThat(studyResultList.size()).isEqualTo(2);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetStudyResultsNotExist()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		Study study = importExampleStudy();
		addStudy(study);

		// Don't add any results
		List<Long> idList = resultService.extractResultIds("1, 2");
		try {
			resultService.getStudyResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.studyResultNotExist(1l));
		}

		// Clean-up
		removeStudy(study);
	}

	private void createTwoStudyResults(Study study) {
		entityManager.getTransaction().begin();
		StudyResult studyResult1 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();
	}

	@Test
	public void checkGetAllowedStudyResultList()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Must have 2 results
		List<StudyResult> studyResultList = resultService
				.getAllowedStudyResultList(admin, admin.getWorker());
		assertThat(studyResultList.size()).isEqualTo(2);

		// Leave the StudyResult but remove admin from the users of the
		// corresponding study
		entityManager.getTransaction().begin();
		studyResultList.get(0).getStudy().removeUser(admin);
		entityManager.getTransaction().commit();

		// Must be empty
		studyResultList = resultService.getAllowedStudyResultList(admin,
				admin.getWorker());
		assertThat(studyResultList.size()).isEqualTo(0);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetAllowedStudyResultListEmpty()
			throws IOException, NoSuchAlgorithmException {
		Study study = importExampleStudy();
		addStudy(study);

		// Don't add any results
		List<StudyResult> studyResultList = resultService
				.getAllowedStudyResultList(admin, admin.getWorker());
		assertThat(studyResultList).isEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetAllowedStudyResultListWrongUser()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		Study study = importExampleStudy();
		addStudy(study);
		createTwoStudyResults(study);

		// Use wrong user to retrieve results
		User testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		List<StudyResult> studyResultList = resultService
				.getAllowedStudyResultList(admin, testUser.getWorker());
		assertThat(studyResultList).isEmpty();

		// Clean-up
		removeStudy(study);
	}

}
