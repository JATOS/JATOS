package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import gui.AbstractGuiTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;

import org.fest.assertions.Fail;
import org.junit.Test;

import persistance.StudyResultDao;
import services.gui.MessagesStrings;
import services.gui.ResultService;
import common.Global;
import controllers.publix.jatos.JatosPublixUtils;

/**
 * Tests ResultService
 * 
 * @author Kristian Lange
 */
public class ResultServiceTest extends AbstractGuiTest {

	private ResultService resultService;
	private JatosPublixUtils jatosPublixUtils;
	private StudyResultDao studyResultDao;

	@Override
	public void before() throws Exception {
		resultService = Global.INJECTOR.getInstance(ResultService.class);
		studyResultDao = Global.INJECTOR.getInstance(StudyResultDao.class);
		jatosPublixUtils = Global.INJECTOR.getInstance(JatosPublixUtils.class);
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
		try {
			resultIdList = resultService.extractResultIds("1,2,3");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		try {
			resultIdList = resultService
					.extractResultIds(" , ,, 1 ,2    ,  3    , ");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		try {
			resultIdList = resultService.extractResultIds("1,b,3");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.resultIdMalformed("b"));
		}

		try {
			resultIdList = resultService.extractResultIds("");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.NO_RESULTS_SELECTED);
		}
	}

	private void checkForProperResultIdList(List<Long> resultIdList) {
		assertThat(resultIdList.size() == 3);
		assertThat(resultIdList.contains(1l));
		assertThat(resultIdList.contains(2l));
		assertThat(resultIdList.contains(3l));
	}

	@Test
	public void checkGetAllComponentResultsAndRemoveAllComponentResults()
			throws BadRequestException, NoSuchAlgorithmException, IOException,
			ForbiddenPublixException, ForbiddenReloadException,
			NotFoundException, ForbiddenException {
		List<Long> idList = resultService.extractResultIds("1, 2");
		try {
			resultService.getAllComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentResultNotExist(1l));
		}

		StudyModel study = importExampleStudy();
		addStudy(study);

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

		List<ComponentResult> componentResultList = null;
		try {
			componentResultList = resultService.getAllComponentResults(idList);
		} catch (NotFoundException e) {
			Fail.fail();
		}
		assertThat(componentResultList.size()).isEqualTo(2);

		// If one of the IDs don't exist it throws an exception
		idList = resultService.extractResultIds("1, 2, 9, 10");
		try {
			resultService.getAllComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentResultNotExist(9l));
		}

		// And try to remove them again - first with the wrong user
		UserModel testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		try {
			resultService.removeAllComponentResults("1, 2", testUser);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotMember(testUser.getName(),
							testUser.getEmail(), study.getId(),
							study.getTitle()));
		}

		// Now remove them for real
		entityManager.getTransaction().begin();
		resultService.removeAllComponentResults("1, 2", admin);
		entityManager.getTransaction().commit();
		idList = resultService.extractResultIds("1, 2");
		try {
			resultService.getAllComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentResultNotExist(1l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetComponentResultData() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException, ForbiddenReloadException,
			BadRequestException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		componentResult1.setData("Thats a first component result.");
		ComponentResult componentResult2 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		componentResult2.setData("Thats a second component result.");
		entityManager.getTransaction().commit();

		List<Long> idList = resultService.extractResultIds("1, 2");
		String componentResultData = null;
		try {
			List<ComponentResult> componentResultList = resultService
					.getAllComponentResults(idList);
			componentResultData = resultService
					.getComponentResultData(componentResultList);
		} catch (NotFoundException e) {
			Fail.fail();
		}
		assertThat(componentResultData)
				.isEqualTo(
						"Thats a first component result.\nThats a second component result.");

		// Do the same but this time without data
		entityManager.getTransaction().begin();
		componentResult1.setData(null);
		componentResult2.setData(null);
		entityManager.getTransaction().commit();

		idList = resultService.extractResultIds("1, 2");
		componentResultData = null;
		try {
			List<ComponentResult> componentResultList = resultService
					.getAllComponentResults(idList);
			componentResultData = resultService
					.getComponentResultData(componentResultList);
		} catch (NotFoundException e) {
			Fail.fail();
		}
		assertThat(componentResultData).isEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetAllStudyResultsAndRemoveAllStudyResults()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException {
		List<Long> idList = resultService.extractResultIds("1, 2");
		try {
			resultService.getAllStudyResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyResultNotExist(1l));
		}

		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		List<StudyResult> studyResultList = null;
		try {
			studyResultList = resultService.getAllStudyResults(idList);
		} catch (NotFoundException e) {
			Fail.fail();
		}
		assertThat(studyResultList.size()).isEqualTo(2);

		// And now try to remove them again - first with the wrong user
		UserModel testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		try {
			resultService.removeAllStudyResults("1,2", testUser);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotMember(testUser.getName(),
							testUser.getEmail(), study.getId(),
							study.getTitle()));
		}
		// Now remove them for real
		entityManager.getTransaction().begin();
		resultService.removeAllStudyResults("1,2", admin);
		entityManager.getTransaction().commit();
		try {
			studyResultList = resultService.getAllStudyResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyResultNotExist(1l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetAllowedStudyResultList()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		List<StudyResult> studyResultList = resultService
				.getAllowedStudyResultList(admin, admin.getWorker());
		assertThat(studyResultList).isEmpty();

		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		studyResultList = resultService.getAllowedStudyResultList(admin,
				admin.getWorker());
		assertThat(studyResultList.size()).isEqualTo(2);

		UserModel testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		studyResultList = resultService.getAllowedStudyResultList(admin,
				testUser.getWorker());
		assertThat(studyResultList).isEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGetStudyResultData() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException, ForbiddenReloadException,
			BadRequestException, NotFoundException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		ComponentResult componentResult11 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult1);
		componentResult11
				.setData("Thats a first component of the first study result.");
		ComponentResult componentResult12 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult1);
		componentResult12
				.setData("Thats a second component of the first study result.");
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		ComponentResult componentResult21 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult2);
		componentResult21
				.setData("Thats a first component of the second study result.");
		ComponentResult componentResult22 = jatosPublixUtils.startComponent(
				study.getFirstComponent(), studyResult2);
		componentResult22
				.setData("Thats a second component of the second study result.");
		entityManager.getTransaction().commit();

		List<Long> idList = resultService.extractResultIds("1, 2");
		List<StudyResult> studyResultList = resultService
				.getAllStudyResults(idList);
		String studyResultData = resultService
				.getStudyResultData(studyResultList);
		assertThat(studyResultData)
				.isEqualTo(
						"Thats a first component of the first study result."
								+ "\n"
								+ "Thats a second component of the first study result."
								+ "\n"
								+ "Thats a first component of the second study result."
								+ "\n"
								+ "Thats a second component of the second study result.");

		// Clean-up
		removeStudy(study);
	}

}
