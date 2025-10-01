package service.gui;

import auth.gui.AuthService;
import com.pivovarit.function.ThrowingFunction;
import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.RequestScope;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.gui.StudyService;
import services.gui.UserService;
import testutils.ContextMocker;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.util.UUID;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for StudyService modeled after UserServiceTest
 *
 * @author Kristian Lange
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class StudyServiceTest extends JatosTest {

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
     * StudyService.clone(): clones a study but does not persist. This includes
     * the Components, Batches and asset directory.
     */
    @Test
    public void checkClone() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);

            Study clone = cloneAndPersistStudy(study);

            // Check properties equal in the original study and the clone
            assertThat(clone.getComponentList().size()).isEqualTo(study.getComponentList().size());
            assertThat(clone.getFirstComponent().get().getTitle()).isEqualTo(study.getFirstComponent().get().getTitle());
            assertThat(clone.getLastComponent().get().getTitle()).isEqualTo(study.getLastComponent().get().getTitle());
            assertThat(clone.getDate()).isEqualTo(study.getDate());
            assertThat(clone.getDescription()).isEqualTo(study.getDescription());
            assertThat(clone.getComments()).isEqualTo(study.getComments());
            assertThat(clone.getJsonData()).isEqualTo(study.getJsonData());
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
        }));
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the users to the members of a study
     */
    @Test
    public void checkChangeUserMember() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction((em) -> {
            studyDao.findById(studyId);
        });

        // Add user foo but not user bar
        jpaApi.withTransaction(unchecked((em) -> {
            createUser("bar@bar.org");
            User userFoo = createUser("foo@foo.org");
            Study s = studyDao.findById(studyId);
            studyService.changeUserMember(s, userFoo, true);
        }));

        // Check that the study's users are admin and user foo
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User userBar = userDao.findByUsername("bar@bar.org");
            assertThat(study.getUserList()).containsOnly(userFoo, admin);
            assertThat(admin.getStudyList()).contains(study);
            assertThat(userFoo.getStudyList()).contains(study);
            assertThat(userBar.getStudyList()).excludes(study);
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
            assertThat(userFoo.getStudyList()).excludes(s);
            assertThat(userBar.getStudyList()).excludes(s);
        });
    }

    /**
     * StudyService.addAllUserMembers(): adding all users to the members of a study
     * StudyService.removeAllUserMembers(): remove all users from the members of a study
     */
    @Test
    public void checkAddAndRemoveAllUserMember() {
        Long studyId = importExampleStudy();

        // Add user foo but not user bar
        jpaApi.withTransaction(unchecked((em) -> {
            createUser("bar@bar.org");
            createUser("foo@foo.org");
            createUser("tee@tee.org");
        }));

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
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);
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
            assertThat(userFoo.getStudyList()).excludes(study);
            assertThat(userBar.getStudyList()).excludes(study);
            assertThat(userTee.getStudyList()).excludes(study);
        });
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the same user
     * twice shouldn't change the outcome
     */
    @Test
    public void checkChangeUserMemberDouble() {
        Long studyId = importExampleStudy();

        // Add user foo twice: no exception should be thrown
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            User userFoo = createUser("foo@foo.org");
            studyService.changeUserMember(study, userFoo, true);
        }));
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(study, userFoo, true);
        }));

        // Check that the study's users are only admin and user foo
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            assertThat(study.getUserList()).containsOnly(userFoo, admin);
        });

        // Remove user foo twice: no exception should be thrown
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(study, userFoo, false);
        }));
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            User userFoo = userDao.findByUsername("foo@foo.org");
            studyService.changeUserMember(study, userFoo, false);
        }));

        // Check that study's users are only admin
        jpaApi.withTransaction((em) -> {
            Study s = studyDao.findById(studyId);
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            assertThat(s.getUserList()).containsOnly(admin);
        });
    }

    /**
     * StudyService.changeUserMember(): study must have at least one member user
     */
    @Test
    public void checkChangeUserMemberAtLeastOne() {
        Long studyId = importExampleStudy();

        // If one tries to remove the last user of a study, an exception is thrown
        jpaApi.withTransaction((em) -> {
            try {
                Study study = studyDao.findById(studyId);
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                studyService.changeUserMember(study, admin, false);
                Fail.fail();
            } catch (ForbiddenException e) {
                // Must throw a ForbiddenException
            }
        });

        // But if the user to be removed isn't a member of the study, it doesn't lead to an exception
        jpaApi.withTransaction((em) -> {
            try {
                Study study = studyDao.findById(studyId);
                User userFoo = createUser("foo@foo.org");
                studyService.changeUserMember(study, userFoo, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
    }

    /**
     * StudyService.changeComponentPosition(): change the position of a
     * component within the study (hint: the first position is 1 and not 0)
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
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            int lastPosition = study.getComponentPosition(study.getLastComponent().get());
            checkChangeToPosition(lastPosition, lastPosition, study.getId());
        });

        // Exception if the position isn't a number
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            try {
                studyService.changeComponentPosition("bla", study, study.getFirstComponent().get());
                Fail.fail();
            } catch (BadRequestException e) {
                // Must throw a BadRequestException
            }
        });

        // Exception if the position isn't within the study
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            try {
                studyService.changeComponentPosition("100", study, study.getFirstComponent().get());
                Fail.fail();
            } catch (BadRequestException e) {
                // Must throw a BadRequestException
            }
        });
    }

    /**
     * StudyService.bindToStudyWithoutDirName(): Update properties of study with
     * properties of updatedStudy (excluding study's dir name).
     */
    @Test
    public void checkBindToStudyWithoutDirName() {
        Long studyId = importExampleStudy();
        Study study = getStudy(studyId);

        StudyProperties updatedProps = new StudyProperties();
        updatedProps.setTitle("Changed Title");
        updatedProps.setDescription("Changed description");
        updatedProps.setComments("Changed comments");
        updatedProps.setStudyEntryMsg("Changed study entry msg");
        updatedProps.setEndRedirectUrl("Changed end redirect url");
        updatedProps.setJsonData("{}");
        updatedProps.setAllowPreview(false);
        updatedProps.setLinearStudy(false);
        updatedProps.setGroupStudy(false);
        updatedProps.setUuid("UUID cannot be changed");
        updatedProps.setDirName("Dir name cannot be changed");

        studyService.bindToStudyWithoutDirName(study, updatedProps);

        // Check changed properties of the study
        assertThat(study.getTitle()).isEqualTo(updatedProps.getTitle());
        assertThat(study.getDescription()).isEqualTo(updatedProps.getDescription());
        assertThat(study.getComments()).isEqualTo(updatedProps.getComments());
        assertThat(study.getStudyEntryMsg()).isEqualTo(updatedProps.getStudyEntryMsg());
        assertThat(study.getEndRedirectUrl()).isEqualTo(updatedProps.getEndRedirectUrl());
        assertThat(study.getJsonData()).isEqualTo(updatedProps.getJsonData());
        assertThat(study.isAllowPreview()).isEqualTo(updatedProps.isAllowPreview());
        assertThat(study.isLinearStudy()).isEqualTo(updatedProps.isLinearStudy());
        assertThat(study.isGroupStudy()).isEqualTo(updatedProps.isGroupStudy());

        // ID, UUID, and dirName shouldn't be changed
        assertThat(study.getId()).isEqualTo(studyId);
        assertThat(study.getUuid()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(study.getDirName()).isEqualTo("potatoCompass");
    }

    /**
     * StudyService.renameStudyAssetsDir()
     */
    @Test
    public void checkRenameStudyAssetsDir() {
        Long studyId = importExampleStudy();
        String oldDirName = jpaApi.withTransaction((em) -> {
            return studyDao.findById(studyId).getDirName();
        });

        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            studyService.renameStudyAssetsDir(study, "changed_dirname");
        }));

        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            assertThat(study.getDirName()).isEqualTo("changed_dirname");
            assertThat(ioUtils.checkStudyAssetsDirExists("changed_dirname")).isTrue();
            assertThat(ioUtils.checkStudyAssetsDirExists(oldDirName)).isFalse();
        });
    }

    /**
     * StudyService.remove()
     */
    @Test
    public void checkRemove() {
        Long studyId = importExampleStudy();

        Study originalStudy = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            studyService.removeStudyInclAssets(study, admin);
            return study;
        }));

        // Check everything was removed
        jpaApi.withTransaction((em) -> {
            // Check that the study is removed from the database
            Study study = studyDao.findById(studyId);
            assertThat(study).isNull();

            // Check that all components are gone
            originalStudy.getComponentList().forEach(c -> assertThat(componentDao.findById(c.getId())).isNull());

            // Check all batches are gone
            originalStudy.getBatchList().forEach(b -> assertThat(batchDao.findById(b.getId())).isNull());

            // This study is removed from all its member users
            originalStudy.getUserList().forEach(u -> assertThat(userDao.findByUsername(u.getUsername()).hasStudy(originalStudy)).isFalse());
        });

        // Check study assets are removed
        assertThat(ioUtils.checkStudyAssetsDirExists(originalStudy.getDirName())).isFalse();
    }

    @Test
    public void checkCreateAndPersistStudyFromProperties() {
        StudyProperties props = new StudyProperties();
        props.setTitle("My Study");
        props.setDescription("Desc");
        props.setComments("Comments");
        props.setStudyEntryMsg("Welcome");
        props.setEndRedirectUrl("http://example.org");
        props.setJsonData("{}");
        props.setAllowPreview(true);
        props.setGroupStudy(false);
        props.setLinearStudy(true);

        Long studyId = jpaApi.withTransaction((em) -> {
            return studyService.createAndPersistStudy(admin, props).getId();
        });

        // Persisted study has a default batch and contains the admin as member
        jpaApi.withTransaction(unchecked(em -> {
            Study study = studyDao.findById(studyId);
            assertThat(study.getId()).isNotNull();
            assertThat(study.getBatchList()).hasSize(1);
            Batch defaultBatch = study.getBatchList().get(0);
            assertThat(defaultBatch.getId()).isNotNull();
            // admin's worker is added to the batch
            assertThat(defaultBatch.getWorkerList()).isNotEmpty();
            assertThat(study.getUserList()).contains(admin);
        }));
    }

    @Test
    public void checkCreateAndPersistStudyFromEntity() {
        Study study = new Study();
        study.setTitle("Study X");
        study.setDescription("D");
        study.setComments("C");
        study.setStudyEntryMsg("Hi");
        study.setEndRedirectUrl("http://x");
        study.setJsonData("{}");
        study.setLinearStudy(false);
        study.setAllowPreview(false);
        study.setGroupStudy(true);

        Study persisted = jpaApi.withTransaction((em) -> {
            return studyService.createAndPersistStudy(admin, study);
        });

        jpaApi.withTransaction(unchecked(em -> {
            assertThat(persisted.getId()).isNotNull();
            assertThat(persisted.getUserList()).contains(admin);
            assertThat(persisted.getBatchList()).isNotEmpty();
        }));
    }

    @Test
    public void checkUpdateStudy() {
        Study study = jpaApi.withTransaction((em) -> {
            Study s = new Study();
            s.setTitle("A");
            s.setDescription("description");
            s.setComments("comments");
            s.setStudyEntryMsg("study entry msg");
            s.setEndRedirectUrl("http://example.org");
            s.setJsonData("{}");
            s.setAllowPreview(true);
            s.setLinearStudy(true);
            s.setGroupStudy(false);
            return studyService.createAndPersistStudy(admin, s);
        });

        // Update description via updateStudy(updatedStudy)
        Study updated = new Study();
        updated.setTitle("changed_title");
        updated.setDescription("changed_description");
        updated.setComments("changed_comments");
        updated.setStudyEntryMsg("changed_study_entry_msg");
        updated.setEndRedirectUrl("changed_end_redirect_url");
        updated.setJsonData("{\"foo\":\"bar\"}");
        updated.setAllowPreview(false);
        updated.setLinearStudy(false);
        updated.setGroupStudy(false);
        updated.setUuid("UUID cannot be changed");
        updated.setDirName("changed_dirname");
        jpaApi.withTransaction((em) -> {
            studyService.updateStudy(study, updated, admin);
        });


        // Verify changed
        Study verifyUpdated = getStudy(study.getId());
        assertThat(verifyUpdated.getTitle()).isEqualTo("changed_title");
        assertThat(verifyUpdated.getDescription()).isEqualTo("changed_description");
        assertThat(verifyUpdated.getComments()).isEqualTo("changed_comments");
        assertThat(verifyUpdated.getStudyEntryMsg()).isEqualTo("changed_study_entry_msg");
        assertThat(verifyUpdated.getEndRedirectUrl()).isEqualTo("changed_end_redirect_url");
        assertThat(verifyUpdated.getJsonData()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(verifyUpdated.isAllowPreview()).isEqualTo(false);
        assertThat(verifyUpdated.isLinearStudy()).isEqualTo(false);
        assertThat(verifyUpdated.isGroupStudy()).isEqualTo(false);
        assertThat(verifyUpdated.getUuid()).isEqualTo(study.getUuid()); // not changed
        assertThat(verifyUpdated.getDirName()).isEqualTo("changed_dirname");
    }

    @Test
    public void checkGetStudyFromIdOrUuid() {
        // Need Play context for RequestScope used by StudyService
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);

        Study study = jpaApi.withTransaction((em) -> {
            Study s = new Study();
            s.setTitle("findable");
            return studyService.createAndPersistStudy(admin, s);
        });

        // By id
        Study byId = jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                studyService.getStudyFromIdOrUuid(String.valueOf(study.getId()))));
        assertThat(byId.getId()).isEqualTo(study.getId());

        // By uuid
        Study byUuid = jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                studyService.getStudyFromIdOrUuid(study.getUuid())));
        assertThat(byUuid.getUuid()).isEqualTo(study.getUuid());
    }

    @Test
    public void checkGetStudyFromIdOrUuidNotFound() {
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);
        String randomUuid = UUID.randomUUID().toString();

        jpaApi.withTransaction(em -> {
            try {
                studyService.getStudyFromIdOrUuid("999999");
                Fail.fail();
            } catch (NotFoundException e) {
                // expected
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });

        jpaApi.withTransaction(em -> {
            try {
                studyService.getStudyFromIdOrUuid(randomUuid);
                Fail.fail();
            } catch (NotFoundException e) {
                // expected
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
    }

    private void checkChangeToPosition(int fromPosition, int toPosition, long studyId) {
        jpaApi.withTransaction(unchecked((em) -> {
            Study s = studyDao.findById(studyId);
            Component c = s.getComponent(fromPosition);
            studyService.changeComponentPosition("" + toPosition, s, c);
            assertThat(s.getComponent(toPosition)).isEqualTo(c);
        }));
    }

    private Study cloneAndPersistStudy(Study studyToBeCloned) {
        return jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            Study studyClone = studyService.clone(studyToBeCloned);
            studyService.createAndPersistStudy(admin, studyClone);
            return studyClone;
        }));
    }
}
