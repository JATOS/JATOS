package services.gui;

import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import org.assertj.core.api.Fail;
import org.junit.Test;
import play.test.Helpers;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.UUID;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StudyService modeled after UserServiceTest
 */
public class StudyServiceIntegrationTest extends JatosTest {

    @Inject
    private StudyService studyService;

    @Inject
    private IOUtils ioUtils;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyDao studyDao;

    @Inject
    private ComponentDao componentDao;

    @Inject
    private BatchDao batchDao;

    /**
     * StudyService.clone(): clones a study but does not persist. This includes the Components, Batches and asset
     * directory.
     */
    @Test
    public void checkClone() {
        Long studyId = importExampleStudy();

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        Study study = studyDao.findByIdWithComponentsAndBatches(studyId);

        Study clone = cloneAndPersistStudy(study);

        // Check properties equal in the original study and the clone
        assertThat(clone.getComponentList().size()).isEqualTo(study.getComponentList().size());
        assertThat(clone.getFirstComponent().orElseThrow().getTitle()).isEqualTo(study.getFirstComponent().orElseThrow().getTitle());
        assertThat(clone.getLastComponent().orElseThrow().getTitle()).isEqualTo(study.getLastComponent().orElseThrow().getTitle());
        assertThat(clone.getDate()).isEqualTo(study.getDate());
        assertThat(clone.getDescription()).isEqualTo(study.getDescription());
        assertThat(clone.getComments()).isEqualTo(study.getComments());
        assertThat(clone.getStudyInput()).isEqualTo(study.getStudyInput());
        assertThat(clone.getUserList()).containsOnly(admin);
        assertThat(clone.getTitle()).isEqualTo(study.getTitle() + " (clone)");

        // Check properties that are not equal
        assertThat(clone.isLocked()).isFalse();
        assertThat(clone.getId()).isNotEqualTo(study.getId());
        assertThat(clone.getId()).isPositive();
        assertThat(clone.getDirName()).isEqualTo(study.getDirName() + "_clone");
        assertThat(clone.getUuid()).isNotEqualTo(study.getUuid());
        assertThat(clone.getUuid()).isNotEmpty();

        assertThat(ioUtils.checkStudyAssetsDirExists(clone.getDirName())).isTrue();
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the users to the members of a study
     */
    @Test
    public void checkChangeUserMember() {
        Long studyId = importExampleStudy();

        // Add user foo but not user bar
        jpaApi.withTransaction((EntityManager em) -> {
            createUser("bar@bar.org");
            User userFoo = createUser("foo@foo.org");
            Study s = studyDao.findById(studyId);
            studyService.changeUserMember(s, userFoo, true);
        });

        // Check that the study's users are admin and user foo
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User userBar = userDao.findByUsername("bar@bar.org");
            assertThat(study.getUserList()).containsOnly(userFoo, admin);
            assertThat(admin.getStudyList()).contains(study);
            assertThat(userFoo.getStudyList()).contains(study);
            assertThat(userBar.getStudyList()).doesNotContain(study);
        });

        // Remove user foo again
        jpaApi.withTransaction((em) -> {
            try {
                Study study = studyDao.findById(studyId);
                User userFoo = userDao.findByUsername("foo@foo.org");
                studyService.changeUserMember(study, userFoo, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });

        // Check that study's user is only the admin user
        jpaApi.withTransaction((em) -> {
            Study s = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User userBar = userDao.findByUsername("bar@bar.org");
            assertThat(s.getUserList()).containsOnly(admin);
            assertThat(admin.getStudyList()).contains(s);
            assertThat(userFoo.getStudyList()).doesNotContain(s);
            assertThat(userBar.getStudyList()).doesNotContain(s);
        });
    }

    /**
     * StudyService.addAllUserMembers(): adding all users to the members of a study StudyService.removeAllUserMembers():
     * remove all users from the members of a study
     */
    @Test
    public void checkAddAndRemoveAllUserMember() {
        Long studyId = importExampleStudy();

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Add user foo but not user bar
        createUser("bar@bar.org");
        createUser("foo@foo.org");
        createUser("tee@tee.org");

        // Add all users to members of study
        jpaApi.withTransaction((em) -> {
            Study s = studyDao.findById(studyId);
            studyService.addAllUserMembers(s);
        });

        // Check that all users are members
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User userBar = userDao.findByUsername("bar@bar.org");
            User userTee = userDao.findByUsername("tee@tee.org");
            assertThat(study.getUserList()).containsOnly(userFoo, userBar, userTee, admin);
            assertThat(admin.getStudyList()).contains(study);
            assertThat(userFoo.getStudyList()).contains(study);
            assertThat(userBar.getStudyList()).contains(study);
            assertThat(userTee.getStudyList()).contains(study);
        });

        // Remove all users from members of study except logged-in user
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            studyService.removeAllUserMembers(study);
        });

        // Check that only logged-in user (admin) is member
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User userBar = userDao.findByUsername("bar@bar.org");
            User userTee = userDao.findByUsername("tee@tee.org");
            assertThat(study.getUserList()).containsOnly(admin);
            assertThat(admin.getStudyList()).contains(study);
            assertThat(userFoo.getStudyList()).doesNotContain(study);
            assertThat(userBar.getStudyList()).doesNotContain(study);
            assertThat(userTee.getStudyList()).doesNotContain(study);
        });
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the same user twice shouldn't change the outcome
     */
    @Test
    public void checkChangeUserMemberDouble() {
        Long studyId = importExampleStudy();

        // Add user foo twice: no exception should be thrown
        {
            Study study = studyDao.findByIdWithUsersAndBatches(studyId);
            User userFoo = createUser("foo@foo.org");
            studyService.changeUserMember(study, userFoo, true);
        }
        {
            Study study = studyDao.findByIdWithUsersAndBatches(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(study, userFoo, true);

        }

        // Check that the study's users are only admin and user foo
        {
            Study study = studyDao.findByIdWithUsersAndBatches(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            assertThat(study.getUserList()).containsOnly(userFoo, admin);
        }

        // Remove user foo twice: no exception should be thrown
        jpaApi.withTransaction(em -> {
            Study s = studyDao.findById(studyId);
            User uFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(s, uFoo, false);
        });
        jpaApi.withTransaction(em -> {
            Study s = studyDao.findById(studyId);
            User uFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(s, uFoo, false);
        });

        // Check that study's users are only admin
        {
            Study study = studyDao.findByIdWithUsersAndBatches(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            assertThat(study.getUserList()).containsOnly(admin);
        }
    }

    /**
     * StudyService.changeUserMember(): study must have at least one member user
     */
    @Test
    public void checkChangeUserMemberAtLeastOne() {
        Long studyId = importExampleStudy();

        // If one tries to remove the last user of a study, an exception is thrown
        try {
            Study study = studyDao.findByIdWithUsers(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            studyService.changeUserMember(study, admin, false);
            Fail.fail();
        } catch (ForbiddenException e) {
            // Must throw a ForbiddenException
        }

        // But if the user to be removed isn't a member of the study, it doesn't lead to an exception
        try {
            Study study = studyDao.findByIdWithUsers(studyId);
            User userFoo = createUser("foo@foo.org");
            studyService.changeUserMember(study, userFoo, false);
        } catch (ForbiddenException e) {
            Fail.fail();
        }
    }

    /**
     * StudyService.changeComponentPosition(): change the position of a component within the study (hint: the first
     * position is 1 and not 0)
     */
    @Test
    public void checkChangeComponentPosition() {
        Long studyId = importExampleStudy();

        // Change the position of the component from first to third
        checkChangeToPosition(1, 3, studyId);

        // And back to first
        checkChangeToPosition(3, 1, studyId);

        // First component to first position -> still first
        checkChangeToPosition(1, 1, studyId);

        // Last component to the last position -> still last
        Study study = studyDao.findByIdWithComponents(studyId);
        int lastPosition = study.getComponentPosition(study.getLastComponent().orElseThrow());
        checkChangeToPosition(lastPosition, lastPosition, study.getId());

        // Exception if the position is a negative number
        study = studyDao.findByIdWithComponents(studyId);
        try {
            studyService.changeComponentPosition(-1, study, study.getFirstComponent().orElseThrow());
            Fail.fail();
        } catch (BadRequestException e) {
            // Must throw a BadRequestException
        }

        // Exception if the position isn't within the study
        study = studyDao.findByIdWithComponents(studyId);
        try {
            studyService.changeComponentPosition(100, study, study.getFirstComponent().orElseThrow());
            Fail.fail();
        } catch (BadRequestException e) {
            // Must throw a BadRequestException
        }
    }

    /**
     * StudyService.bindToStudyWithoutDirName(): Update properties of study with properties of updatedStudy (excluding
     * study's dir name).
     */
    @Test
    public void checkBindToStudy() {
        Long studyId = importExampleStudy();
        Study study = getStudy(studyId);

        StudyProperties updatedProps = new StudyProperties();
        updatedProps.setTitle("Changed Title");
        updatedProps.setDescription("Changed description");
        updatedProps.setComments("Changed comments");
        updatedProps.setDirName("Changed dir name");
        updatedProps.setStudyEntryMsg("Changed study entry msg");
        updatedProps.setEndRedirectUrl("Changed end redirect url");
        updatedProps.setStudyInput("{}");
        updatedProps.setAllowPreview(false);
        updatedProps.setLinearStudy(false);
        updatedProps.setGroupStudy(false);
        updatedProps.setUuid("UUID cannot be changed");

        studyService.bindToStudy(study, updatedProps);

        // Check changed properties of the study
        assertThat(study.getTitle()).isEqualTo(updatedProps.getTitle());
        assertThat(study.getDescription()).isEqualTo(updatedProps.getDescription());
        assertThat(study.getComments()).isEqualTo(updatedProps.getComments());
        assertThat(study.getDirName()).isEqualTo(updatedProps.getDirName());
        assertThat(study.getStudyEntryMsg()).isEqualTo(updatedProps.getStudyEntryMsg());
        assertThat(study.getEndRedirectUrl()).isEqualTo(updatedProps.getEndRedirectUrl());
        assertThat(study.getStudyInput()).isEqualTo(updatedProps.getStudyInput());
        assertThat(study.isAllowPreview()).isEqualTo(updatedProps.isAllowPreview());
        assertThat(study.isLinearStudy()).isEqualTo(updatedProps.isLinearStudy());
        assertThat(study.isGroupStudy()).isEqualTo(updatedProps.isGroupStudy());

        // ID and UUID shouldn't be changed
        assertThat(study.getId()).isEqualTo(studyId);
        assertThat(study.getUuid()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
    }

    /**
     * StudyService.renameStudyAssetsDir()
     */
    @Test
    public void checkRenameStudyAssetsDir() {
        Long studyId = importExampleStudy();
        String oldDirName = studyDao.findById(studyId).getDirName();

        Study study = studyDao.findById(studyId);
        studyService.renameStudyAssetsDir(study, "changed_dirname");

        study = studyDao.findById(studyId);
        assertThat(study.getDirName()).isEqualTo("changed_dirname");
        assertThat(ioUtils.checkStudyAssetsDirExists("changed_dirname")).isTrue();
        assertThat(ioUtils.checkStudyAssetsDirExists(oldDirName)).isFalse();
    }

    /**
     * StudyService.remove()
     */
    @Test
    public void checkRemove() {
        Long studyId = importExampleStudy();

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        Study originalStudy = jpaApi.withTransaction(em -> {
            Study study = studyDao.findById(studyId);
            studyService.removeStudyInclAssets(study);
            return study;
        });

        // Check everything was removed
        // Check that the study is removed from the database
        Study study = studyDao.findById(studyId);
        assertThat(study).isNull();

        // Check that all components are gone
        originalStudy.getComponentList().forEach(c -> assertThat(componentDao.findById(c.getId())).isNull());

        // Check all batches are gone
        originalStudy.getBatchList().forEach(b -> assertThat(batchDao.findById(b.getId())).isNull());

        // This study is removed from all its member users
        originalStudy.getUserList().forEach(u -> assertThat(userDao.findByUsername(u.getUsername()).hasStudy(originalStudy)).isFalse());

        // Check study assets are removed
        assertThat(ioUtils.checkStudyAssetsDirExists(originalStudy.getDirName())).isFalse();
    }

    @Test
    public void checkCreateAndPersistStudyFromStudy() {
        Study study = new Study();
        study.setTitle("My Study");
        study.setDescription("Desc");
        study.setComments("Comments");
        study.setStudyEntryMsg("Welcome");
        study.setEndRedirectUrl("http://example.org");
        study.setStudyInput("{}");
        study.setAllowPreview(true);
        study.setGroupStudy(false);
        study.setLinearStudy(true);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        study = studyService.createAndPersistStudy(study);

        assertThat(study.getId()).isPositive();
        assertThat(study.getUuid()).isNotEmpty();

        // Persisted study has a default batch and contains the admin as member
        long studyId = study.getId();
        jpaApi.withTransaction(em -> {
            Study s = studyDao.findById(studyId);
            assertThat(s.getId()).isNotNull();
            assertThat(s.getBatchList()).hasSize(1);
            Batch defaultBatch = s.getBatchList().get(0);
            assertThat(defaultBatch.getId()).isNotNull();
            // admin's worker is added to the batch
            assertThat(defaultBatch.getWorkerList()).isNotEmpty();
            assertThat(s.getUserList()).contains(admin);
        });
    }

    @Test
    public void checkCreateAndPersistStudyFromEntity() {
        Study study = new Study();
        study.setTitle("Study X");
        study.setDescription("D");
        study.setComments("C");
        study.setStudyEntryMsg("Hi");
        study.setEndRedirectUrl("http://x");
        study.setStudyInput("{}");
        study.setLinearStudy(false);
        study.setAllowPreview(false);
        study.setGroupStudy(true);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        Study persisted = studyService.createAndPersistStudy(study);

        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getUserList()).contains(admin);
        assertThat(persisted.getBatchList()).isNotEmpty();
    }

    @Test
    public void checkUpdateStudy() {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        Study study = new Study();
        study.setTitle("A");
        study.setDescription("description");
        study.setComments("comments");
        study.setStudyEntryMsg("study entry msg");
        study.setEndRedirectUrl("http://example.org");
        study.setStudyInput("{}");
        study.setAllowPreview(true);
        study.setLinearStudy(true);
        study.setGroupStudy(false);
        study = studyService.createAndPersistStudy(study);

        // Update description via updateStudy(updatedStudy)
        Study updated = new Study();
        updated.setTitle("changed_title");
        updated.setDescription("changed_description");
        updated.setComments("changed_comments");
        updated.setStudyEntryMsg("changed_study_entry_msg");
        updated.setEndRedirectUrl("changed_end_redirect_url");
        updated.setStudyInput("{\"foo\":\"bar\"}");
        updated.setAllowPreview(false);
        updated.setLinearStudy(false);
        updated.setGroupStudy(false);
        updated.setUuid("UUID cannot be changed");
        updated.setDirName("changed_dirname");

        studyService.updateStudyAndRenameAssets(study, updated);

        // Verify changed
        Study verifyUpdated = getStudy(study.getId());
        assertThat(verifyUpdated.getTitle()).isEqualTo("changed_title");
        assertThat(verifyUpdated.getDescription()).isEqualTo("changed_description");
        assertThat(verifyUpdated.getComments()).isEqualTo("changed_comments");
        assertThat(verifyUpdated.getStudyEntryMsg()).isEqualTo("changed_study_entry_msg");
        assertThat(verifyUpdated.getEndRedirectUrl()).isEqualTo("changed_end_redirect_url");
        assertThat(verifyUpdated.getStudyInput()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(verifyUpdated.isAllowPreview()).isEqualTo(false);
        assertThat(verifyUpdated.isLinearStudy()).isEqualTo(false);
        assertThat(verifyUpdated.isGroupStudy()).isEqualTo(false);
        assertThat(verifyUpdated.getUuid()).isEqualTo(study.getUuid()); // not changed
        assertThat(verifyUpdated.getDirName()).isEqualTo("changed_dirname");
    }

    @Test
    public void checkGetStudyFromIdOrUuid() {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        Study study = new Study();
        study.setTitle("findable");
        study = studyService.createAndPersistStudy(study);

        // By id
        Study byId = studyService.getStudyFromIdOrUuid(String.valueOf(study.getId()));
        assertThat(byId.getId()).isEqualTo(study.getId());

        // By uuid
        Study byUuid = studyService.getStudyFromIdOrUuid(study.getUuid());
        assertThat(byUuid.getUuid()).isEqualTo(study.getUuid());
    }

    @Test
    public void checkGetStudyFromIdOrUuidNotFound() {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);
        String randomUuid = UUID.randomUUID().toString();

        Study study = studyService.getStudyFromIdOrUuid("999999");
        assertThat(study).isNull();

        study = studyService.getStudyFromIdOrUuid(randomUuid);
        assertThat(study).isNull();
    }

    private void checkChangeToPosition(int fromPosition, int toPosition, long studyId) {
        Study s = studyDao.findByIdWithComponents(studyId);
        Component c = s.getComponent(fromPosition);
        studyService.changeComponentPosition(toPosition, s, c);
        assertThat(s.getComponent(toPosition)).isEqualTo(c);
    }

    private Study cloneAndPersistStudy(Study studyToBeCloned) {
        Study studyClone = studyService.clone(studyToBeCloned);
        return studyService.createAndPersistStudy(studyClone);
    }
}
