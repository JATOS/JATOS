package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import common.AbstractTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import models.Component;
import models.Study;
import models.User;
import models.workers.PersonalSingleWorker;
import models.workers.JatosWorker;
import models.workers.PersonalMultipleWorker;

import org.fest.assertions.Fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.db.jpa.JPA;
import services.StudyService;
import utils.IOUtils;
import utils.MessagesStrings;
import common.Global;

/**
 * Tests StudyService
 * 
 * @author Kristian Lange
 */
public class StudyServiceTest extends AbstractTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private StudyService studyService;

	@Override
	public void before() throws Exception {
		studyService = Global.INJECTOR.getInstance(StudyService.class);
		mockContext();
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
		Study study = importExampleStudy();
		addStudy(study);
		entityManager.getTransaction().begin();
		Study clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		Study cloneInDb = studyDao.findByUuid(clone.getUuid());

		// Equal
		assertThat(cloneInDb.getAllowedWorkerTypeList()).containsOnly(
				JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE,
				PersonalMultipleWorker.WORKER_TYPE);
		assertThat(cloneInDb.getComponentList().size()).isEqualTo(
				study.getComponentList().size());
		assertThat(cloneInDb.getFirstComponent().getTitle()).isEqualTo(
				study.getFirstComponent().getTitle());
		assertThat(cloneInDb.getLastComponent().getTitle()).isEqualTo(
				study.getLastComponent().getTitle());
		assertThat(cloneInDb.getDate()).isEqualTo(study.getDate());
		assertThat(cloneInDb.getDescription())
				.isEqualTo(study.getDescription());
		assertThat(cloneInDb.getComments())
				.isEqualTo(study.getComments());
		assertThat(cloneInDb.getJsonData()).isEqualTo(study.getJsonData());
		assertThat(cloneInDb.getUserList()).containsOnly(admin);
		assertThat(cloneInDb.getTitle()).isEqualTo(
				study.getTitle() + " (clone)");

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
	public void checkUpdateStudy() throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Study updatedStudy = studyService.cloneStudy(study, admin);
		updatedStudy.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		updatedStudy.removeAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		updatedStudy.getComponentList().remove(0);
		updatedStudy.getLastComponent().setTitle("Changed title");
		updatedStudy.setDescription("Changed description");
		updatedStudy.setComments("Changed comments");
		updatedStudy.setJsonData("{}");
		updatedStudy.setGroupStudy(true);
		// TODO
//		updatedStudy.setMinGroupSize(5);
//		updatedStudy.setMaxGroupSize(5);
		updatedStudy.setTitle("Changed Title");
		updatedStudy.setUuid("changed uuid");
		updatedStudy.getUserList().remove(admin);
		long studyId = study.getId();

		entityManager.getTransaction().begin();
		studyService.updatePropertiesWODirName(study, updatedStudy);
		entityManager.getTransaction().commit();

		// Changed
		assertThat(study.getTitle()).isEqualTo(updatedStudy.getTitle());
		assertThat(study.getDescription()).isEqualTo(
				updatedStudy.getDescription());
		assertThat(study.getComments()).isEqualTo(updatedStudy.getComments());
		assertThat(study.getJsonData()).isEqualTo(updatedStudy.getJsonData());
		assertThat(study.getAllowedWorkerTypeList()).containsOnly(
				JatosWorker.WORKER_TYPE);
		assertThat(study.isGroupStudy()).isEqualTo(updatedStudy.isGroupStudy());
		// TODO
//		assertThat(study.getMinGroupSize()).isEqualTo(
//				updatedStudy.getMinGroupSize());
//		assertThat(study.getMaxGroupSize()).isEqualTo(
//				updatedStudy.getMaxGroupSize());

		// Unchanged
		assertThat(study.getComponentList().size()).isEqualTo(7);
		assertThat(study.getComponent(1).getTitle()).isEqualTo(
				"Show JSON input ");
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(study.getId()).isEqualTo(studyId);
		assertThat(study.getUserList()).contains(admin);
		assertThat(study.getUuid()).isEqualTo(
				"5c85bd82-0258-45c6-934a-97ecc1ad6617");

		// Clean-up
		removeStudy(study);
		removeStudy(updatedStudy);
	}

	@Test
	public void checkExchangeUsers() throws NoSuchAlgorithmException,
			IOException {
		Study study = importExampleStudy();
		addStudy(study);

		User userBla = createAndPersistUser("bla@bla.com", "Bla", "bla");
		createAndPersistUser("blu@blu.com", "Blu", "blu");

		entityManager.getTransaction().begin();
		try {
			String[] userList = { "admin", "bla@bla.com" };
			studyService.exchangeUsers(study, userList);
		} catch (BadRequestException e) {
			Fail.fail();
		}
		entityManager.getTransaction().commit();

		Study studyInDb = studyDao.findByUuid(study.getUuid());
		assertThat(studyInDb.getUserList()).containsOnly(userBla, admin);

		// Empty user list
		try {
			String[] userList = {};
			studyService.exchangeUsers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.STUDY_AT_LEAST_ONE_USER);
		}

		// Not existent user
		try {
			String[] userList = { "not_exist", "admin" };
			studyService.exchangeUsers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.userNotExist("not_exist"));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void testCheckStudyLocked() throws NoSuchAlgorithmException,
			IOException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException e) {
			Fail.fail();
		}

		study.setLocked(true);
		try {
			studyService.checkStudyLocked(study);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyLocked(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void testCheckStandardForStudy() throws NoSuchAlgorithmException,
			IOException {
		try {
			studyService.checkStandardForStudy(null, 1l, admin);
			Fail.fail();
		} catch (ForbiddenException e) {
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotExist(1l));
		}

		Study study = importExampleStudy();
		addStudy(study);
		try {
			studyService.checkStandardForStudy(study, study.getId(), admin);
		} catch (ForbiddenException e) {
			Fail.fail();
		} catch (BadRequestException e) {
			Fail.fail();
		}

		study.getUserList().remove(admin);
		try {
			studyService.checkStandardForStudy(study, study.getId(), admin);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotUser(admin.getName(),
							admin.getEmail(), study.getId(), study.getTitle()));
		} catch (BadRequestException e) {
			Fail.fail();
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkChangeComponentPosition() throws NoSuchAlgorithmException,
			IOException {
		Study study = importExampleStudy();
		addStudy(study);

		// First component to third position
		Component component = study.getFirstComponent();
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition("3", study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(3)).isEqualTo(component);

		// Back to first
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition("1", study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(1)).isEqualTo(component);

		// First component to first position -> still first
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition("1", study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(1)).isEqualTo(component);

		// Last component to last position -> still last
		component = study.getLastComponent();
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition("7", study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getLastComponent()).isEqualTo(component);

		// NumberFormatException
		try {
			studyService.changeComponentPosition("bla", study, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
		}

		// IndexOutOfBoundsException
		try {
			studyService.changeComponentPosition("100", study, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyReorderUnknownPosition("100",
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkBindStudyFromRequest() {
		Map<String, String[]> formMap = new HashMap<String, String[]>();
		String[] titleArray = { "This is a title" };
		formMap.put(Study.TITLE, titleArray);
		String[] descArray = { "This is a description" };
		formMap.put(Study.DESCRIPTION, descArray);
		String[] commentsArray = { "This is a comment" };
		formMap.put(Study.COMMENTS, commentsArray);
		String[] dirNameArray = { "dir_name" };
		formMap.put(Study.DIRNAME, dirNameArray);
		String[] groupStudyArray = { "true" };
		formMap.put(Study.GROUP_STUDY, groupStudyArray);
		// TODO
//		String[] minGroupSizeArray = { "5" };
//		formMap.put(Study.MIN_GROUP_SIZE, minGroupSizeArray);
//		String[] maxGroupSizeArray = { "5" };
//		formMap.put(Study.MAX_GROUP_SIZE, maxGroupSizeArray);
		String[] jsonArray = { "{}" };
		formMap.put(Study.JSON_DATA, jsonArray);
		String[] allowedWorkerArray = { JatosWorker.WORKER_TYPE };
		formMap.put(Study.ALLOWED_WORKER_LIST, allowedWorkerArray);

		Study study = studyService.bindStudyFromRequest(formMap);
		assertThat(study.getTitle()).isEqualTo("This is a title");
		assertThat(study.getDescription()).isEqualTo("This is a description");
		assertThat(study.getComments()).isEqualTo("This is a comment");
		assertThat(study.getDirName()).isEqualTo("dir_name");
		assertThat(study.isGroupStudy()).isTrue();
		// TODO
//		assertThat(study.getMinGroupSize()).isEqualTo(5);
//		assertThat(study.getMaxGroupSize()).isEqualTo(5);
		assertThat(study.getJsonData()).isEqualTo("{}");
		assertThat(study.getAllowedWorkerTypeList()).containsOnly(
				JatosWorker.WORKER_TYPE);
	}

	@Test
	public void checkRenameStudyAssetsDir() throws NoSuchAlgorithmException,
			IOException {
		Study study = importExampleStudy();
		addStudy(study);

		String oldDirName = study.getDirName();

		entityManager.getTransaction().begin();
		studyService.renameStudyAssetsDir(study, "changed_dirname");
		entityManager.getTransaction().commit();

		assertThat(study.getDirName()).isEqualTo("changed_dirname");
		assertThat(IOUtils.checkStudyAssetsDirExists("changed_dirname"))
				.isTrue();
		assertThat(IOUtils.checkStudyAssetsDirExists(oldDirName)).isFalse();

		// Clean-up
		removeStudy(study);
	}

}
