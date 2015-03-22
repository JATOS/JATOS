package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import gui.AbstractGuiTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.UserModel;
import models.workers.ClosedStandaloneWorker;
import models.workers.JatosWorker;
import models.workers.TesterWorker;

import org.fest.assertions.Fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.db.jpa.JPA;
import services.gui.MessagesStrings;
import services.gui.StudyService;
import utils.IOUtils;

import common.Global;

/**
 * Tests StudyService
 * 
 * @author Kristian Lange
 */
public class StudyServiceTest extends AbstractGuiTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private StudyService studyService;

	@Override
	public void before() throws Exception {
		studyService = Global.INJECTOR.getInstance(StudyService.class);
		mockContext();
		// Don't know why, but we have to bind entityManager again
		JPA.bindForCurrentThread(entityManager);
	}

	@Override
	public void after() throws Exception {
		JPA.bindForCurrentThread(null);
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkCloneStudy() throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);
		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		StudyModel cloneInDb = studyDao.findByUuid(clone.getUuid());

		// Equal
		assertThat(cloneInDb.getAllowedWorkerList()).containsOnly(
				JatosWorker.WORKER_TYPE, ClosedStandaloneWorker.WORKER_TYPE,
				TesterWorker.WORKER_TYPE);
		assertThat(cloneInDb.getComponentList().size()).isEqualTo(
				study.getComponentList().size());
		assertThat(cloneInDb.getFirstComponent().getTitle()).isEqualTo(
				study.getFirstComponent().getTitle());
		assertThat(cloneInDb.getLastComponent().getTitle()).isEqualTo(
				study.getLastComponent().getTitle());
		assertThat(cloneInDb.getDate()).isEqualTo(study.getDate());
		assertThat(cloneInDb.getDescription())
				.isEqualTo(study.getDescription());
		assertThat(cloneInDb.getJsonData()).isEqualTo(study.getJsonData());
		assertThat(cloneInDb.getMemberList()).containsOnly(admin);
		assertThat(cloneInDb.getTitle()).isEqualTo(study.getTitle());

		// Not equal
		assertThat(cloneInDb.isLocked()).isFalse();
		assertThat(cloneInDb.getId()).isNotEqualTo(study.getId());
		assertThat(cloneInDb.getId()).isPositive();
		assertThat(cloneInDb.getDirName()).isEqualTo(
				study.getDirName() + "_clone");
		assertThat(cloneInDb.getUuid()).isNotEqualTo(study.getUuid());
		assertThat(cloneInDb.getUuid()).isNotEmpty();

		assertThat(IOUtils.checkStudyAssetsDirExists(cloneInDb.getDirName()))
				.isTrue();

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkExchangeMembers() throws NoSuchAlgorithmException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		UserModel userBla = createAndPersistUser("bla@bla.com", "Bla", "bla");
		createAndPersistUser("blu@blu.com", "Blu", "blu");

		entityManager.getTransaction().begin();
		try {
			String[] userList = { "admin", "bla@bla.com" };
			studyService.exchangeMembers(study, userList);
		} catch (BadRequestException e) {
			Fail.fail();
		}
		entityManager.getTransaction().commit();

		StudyModel studyInDb = studyDao.findByUuid(study.getUuid());
		assertThat(studyInDb.getMemberList()).containsOnly(userBla, admin);

		// Empty user list
		try {
			String[] userList = {};
			studyService.exchangeMembers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.STUDY_AT_LEAST_ONE_MEMBER);
		}

		// Not existent user
		try {
			String[] userList = { "not_exist", "admin" };
			studyService.exchangeMembers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.userNotExist("not_exist"));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkUpdateStudy() throws NoSuchAlgorithmException, IOException {

		StudyModel study = importExampleStudy();
		addStudy(study);

		StudyModel updatedStudy = studyService.cloneStudy(study, admin);
		updatedStudy.removeAllowedWorker(ClosedStandaloneWorker.WORKER_TYPE);
		updatedStudy.removeAllowedWorker(TesterWorker.WORKER_TYPE);
		updatedStudy.getComponentList().remove(0);
		updatedStudy.getLastComponent().setTitle("Changed title");
		updatedStudy.setDescription("Changed description");
		updatedStudy.setJsonData("{}");
		updatedStudy.setTitle("Changed Title");
		updatedStudy.setUuid("changed uuid");
		updatedStudy.getMemberList().remove(admin);
		long studyId = study.getId();

		entityManager.getTransaction().begin();
		studyService.updateStudy(study, updatedStudy);
		entityManager.getTransaction().commit();

		// Changed
		assertThat(study.getTitle()).isEqualTo(updatedStudy.getTitle());
		assertThat(study.getDescription()).isEqualTo(
				updatedStudy.getDescription());
		assertThat(study.getJsonData()).isEqualTo(updatedStudy.getJsonData());
		assertThat(study.getAllowedWorkerList()).containsOnly(
				JatosWorker.WORKER_TYPE);

		// Unchanged
		assertThat(study.getComponentList().size() == 8).isTrue();
		assertThat(study.getComponent(1).getTitle()).isEqualTo("Hello World");
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(study.getId()).isEqualTo(studyId);
		assertThat(study.getMemberList().contains(admin)).isTrue();
		assertThat(study.getUuid()).isEqualTo(
				"5c85bd82-0258-45c6-934a-97ecc1ad6617");

		// Clean-up
		removeStudy(study);
		removeStudy(updatedStudy);
	}

}
