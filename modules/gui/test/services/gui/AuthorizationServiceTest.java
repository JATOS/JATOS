package services.gui;

import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.*;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import utils.common.Helpers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.fest.assertions.Assertions.assertThat;

public class AuthorizationServiceTest {

    private MockedStatic<Helpers> helpersMock;

    private final AuthorizationService authorizationService = new AuthorizationService();

    @After
    public void tearDown() {
        if (helpersMock != null) {
            helpersMock.close();
        }
    }

    private Study newStudy(long id) {
        Study study = new Study();
        study.setId(id);
        study.setUuid(UUID.randomUUID().toString());
        return study;
    }

    private User newUser(String username) {
        return new User(username, username, username + "@example.org");
    }

    private long batchIdCounter = 1;

    private Batch newBatch(Study study) {
        Batch batch = new Batch();
        batch.setUuid(UUID.randomUUID().toString());
        batch.setId(batchIdCounter++); // ensure non-null unique ID for equals()
        batch.setStudy(study);
        study.addBatch(batch);
        return batch;
    }

    private Component newComponent(Study study, long id) {
        Component c = new Component();
        c.setId(id);
        c.setStudy(study);
        return c;
    }

    private StudyLink newStudyLink(Batch batch) {
        return new StudyLink(batch, "JATOS");
    }

    private GroupResult newGroupResult(Batch batch, long id) {
        GroupResult gr = new GroupResult();
        gr.setId(id);
        gr.setBatch(batch);
        return gr;
    }

    private ComponentResult newComponentResult(Component component) {
        ComponentResult cr = new ComponentResult();
        cr.setComponent(component);
        return cr;
    }

    private StudyResult newStudyResult(Study study) {
        StudyResult sr = new StudyResult();
        sr.setStudy(study);
        return sr;
    }

    private Worker newWorker(long id) {
        JatosWorker worker = new JatosWorker();
        worker.setId(id);
        return worker;
    }

    private UserProperties newUserProps(String username) {
        UserProperties props = new UserProperties();
        props.setUsername(username);
        return props;
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessComponent_nullComponent_throwsNotFound() throws Exception {
        authorizationService.canUserAccessComponent(null, newUser("u"));
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessBatch_nullBatch_throwsNotFound() throws Exception {
        authorizationService.canUserAccessBatch(null, newUser("u"));
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessStudyLink_nullStudyLink_throwsNotFound() throws Exception {
        authorizationService.canUserAccessStudyLink(null, newUser("u"));
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessGroupResult_nullGroupResult_throwsNotFound() throws Exception {
        authorizationService.canUserAccessGroupResult(null, newUser("u"));
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessComponentResult_nullComponentResult_throwsNotFound() throws Exception {
        authorizationService.canUserAccessComponentResult(null, newUser("u"), false);
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessStudyResult_nullStudyResult_throwsNotFound() throws Exception {
        authorizationService.canUserAccessStudyResult(null, newUser("u"), false);
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessWorker_nullWorker_throwsNotFound() throws Exception {
        authorizationService.canUserAccessWorker(newUser("u"), null);
    }

    @Test
    public void canUserAccessStudy_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessStudy(study, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessStudy(study, user, false);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStudyNotLocked_lockedStudy_throwsForbidden() throws Exception {
        Study study = newStudy(1L);
        study.setLocked(true);
        authorizationService.checkStudyNotLocked(study);
    }

    @Test
    public void canUserAccessStudyLink_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Batch batch = newBatch(study);
        StudyLink studyLink = newStudyLink(batch);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessStudyLink(studyLink, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessStudyLink(studyLink, user, false);
    }

    @Test
    public void checkAdminOrSelf_adminOrSelf_ok_otherForbidden() throws Exception {
        User admin = newUser("admin");
        admin.addRole(User.Role.ADMIN);
        User u1 = newUser("u1");
        User u2 = newUser("u2");

        authorizationService.checkAdminOrSelf(admin, u1);
        authorizationService.checkAdminOrSelf(u1, u1);

        boolean threw = false;
        try {
            authorizationService.checkAdminOrSelf(u1, u2);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void canUserAccessWorker_userHasWorkerInStudy_ok() throws Exception {
        User user = newUser("u");
        Study study = newStudy(1L);
        Batch batch = newBatch(study);
        Worker worker = newWorker(1L);
        batch.getWorkerList().add(worker);
        user.addStudy(study);

        authorizationService.canUserAccessWorker(user, worker);
    }

    @Test
    public void canUserAccessComponent_andStudyLink_withSuperuser_ok() throws Exception {
        Study study = newStudy(1L);
        Component component = newComponent(study, 10L);
        Batch batch = newBatch(study);
        StudyLink studyLink = newStudyLink(batch);
        User user = newUser("super");

        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(user)).thenReturn(true);

        authorizationService.canUserAccessComponent(component, user);
        authorizationService.canUserAccessStudyLink(studyLink, user);
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessStudy_notMemberOrSuperuser_throwsForbidden() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("u");

        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(user)).thenReturn(false);

        authorizationService.canUserAccessStudy(study, user);
    }

    @Test
    public void canUserAccessResults_chain_ok() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);

        Batch batch = newBatch(study);
        GroupResult groupResult = newGroupResult(batch, 1L);
        Component component = newComponent(study, 2L);
        ComponentResult componentResult = newComponentResult(component);
        StudyResult studyResult = newStudyResult(study);

        authorizationService.canUserAccessGroupResult(groupResult, user);
        authorizationService.canUserAccessComponentResult(componentResult, user, false);
        authorizationService.canUserAccessStudyResult(studyResult, user, false);
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessStudy_nullStudy_throwsNotFound() throws Exception {
        authorizationService.canUserAccessStudy(null, newUser("u"));
    }

    @Test
    public void canUserAccessComponent_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Component component = newComponent(study, 1L);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessComponent(component, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessComponent(component, user, false);
    }

    @Test
    public void canUserAccessBatch_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Batch batch = newBatch(study);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessBatch(batch, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessBatch(batch, user, false);
    }

    @Test
    public void canUserAccessGroupResult_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Batch batch = newBatch(study);
        GroupResult groupResult = newGroupResult(batch, 1L);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessGroupResult(groupResult, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessGroupResult(groupResult, user, false);
    }

    @Test
    public void canUserAccessComponentResult_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Component component = newComponent(study, 1L);
        ComponentResult componentResult = newComponentResult(component);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessComponentResult(componentResult, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessComponentResult(componentResult, user, false);
    }

    @Test
    public void canUserAccessStudyResult_lockedStudy_respectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        StudyResult studyResult = newStudyResult(study);

        study.setLocked(true);

        boolean threw = false;
        try {
            authorizationService.canUserAccessStudyResult(studyResult, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        authorizationService.canUserAccessStudyResult(studyResult, user, false);
    }

    @Test(expected = NotFoundException.class)
    public void checkAdminOrSelf_nullUser_throwsNotFound() throws Exception {
        authorizationService.checkAdminOrSelf(newUser("u"), null);
    }

    @Test(expected = ForbiddenException.class)
    public void checkNotDefaultBatch_defaultBatch_throwsForbidden() throws Exception {
        Study study = newStudy(1L);
        Batch batch1 = newBatch(study);
        newBatch(study);

        authorizationService.checkNotDefaultBatch(batch1);
    }

    @Test
    public void checkNotDefaultBatch_nonDefaultBatch_ok() throws Exception {
        Study study = newStudy(1L);
        newBatch(study);
        Batch batch2 = newBatch(study);

        authorizationService.checkNotDefaultBatch(batch2);
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessWorker_userMissingWorker_forbidden() throws Exception {
        User user = newUser("u");
        Study study = newStudy(1L);
        newBatch(study);
        user.addStudy(study);

        authorizationService.canUserAccessWorker(user, newWorker(99L));
    }

    @Test
    public void canUserAccessComponentResults_list_ok_andLockedRespectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        Component component = newComponent(study, 1L);
        ComponentResult c1 = newComponentResult(component);
        ComponentResult c2 = newComponentResult(component);
        List<ComponentResult> results = Arrays.asList(c1, c2);

        authorizationService.canUserAccessComponentResults(results, user, false);

        study.setLocked(true);
        boolean threw = false;
        try {
            authorizationService.canUserAccessComponentResults(results, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void canUserAccessStudyResults_list_ok_andLockedRespectsFlag() throws Exception {
        Study study = newStudy(1L);
        User user = newUser("member");
        study.addUser(user);
        StudyResult s1 = newStudyResult(study);
        StudyResult s2 = newStudyResult(study);
        List<StudyResult> results = Arrays.asList(s1, s2);

        authorizationService.canUserAccessStudyResults(results, user, false);

        study.setLocked(true);
        boolean threw = false;
        try {
            authorizationService.canUserAccessStudyResults(results, user, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void checkAuthMethodIsDbOrLdap_newUserProps_ok_andInvalidForbidden() throws Exception {
        NewUserProperties props = new NewUserProperties();
        props.setAuthMethod(User.AuthMethod.DB);
        authorizationService.checkAuthMethodIsDbOrLdap(props);

        props.setAuthMethod(User.AuthMethod.OIDC);
        boolean threw = false;
        try {
            authorizationService.checkAuthMethodIsDbOrLdap(props);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test(expected = NotFoundException.class)
    public void checkAuthMethodIsDbOrLdap_nullUser_throwsNotFound() throws Exception {
        authorizationService.checkAuthMethodIsDbOrLdap((User) null);
    }

    @Test
    public void checkAuthMethodIsDbOrLdap_user_ok_andInvalidForbidden() throws Exception {
        User user = newUser("u");
        user.setAuthMethod(User.AuthMethod.LDAP);
        authorizationService.checkAuthMethodIsDbOrLdap(user);

        user.setAuthMethod(User.AuthMethod.OIDC);
        boolean threw = false;
        try {
            authorizationService.checkAuthMethodIsDbOrLdap(user);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test(expected = NotFoundException.class)
    public void checkNotUserAdmin_nullUser_throwsNotFound() throws Exception {
        authorizationService.checkNotUserAdmin(null);
    }

    @Test(expected = ForbiddenException.class)
    public void checkNotUserAdmin_adminUser_throwsForbidden() throws Exception {
        authorizationService.checkNotUserAdmin(newUser("admin"));
    }

    @Test
    public void checkNotUserAdmin_nonAdmin_ok() throws Exception {
        authorizationService.checkNotUserAdmin(newUser("user"));
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_adminPasswordChange_forbidden() throws Exception {
        UserProperties props = newUserProps("admin");
        props.setPassword("secret");

        User signedinUser = newUser("other");
        User user = newUser("admin");

        boolean threw = false;
        try {
            authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_adminDeactivate_forbidden() throws Exception {
        UserProperties props = newUserProps("admin");
        props.setActive(false);

        User signedinUser = newUser("admin");
        User user = newUser("admin");

        boolean threw = false;
        try {
            authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_selfDeactivate_forbidden() throws Exception {
        UserProperties props = newUserProps("user");
        props.setActive(false);

        User signedinUser = newUser("user");
        User user = signedinUser;

        boolean threw = false;
        try {
            authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_ldapPasswordChange_forbidden() throws Exception {
        UserProperties props = newUserProps("user");
        props.setPassword("secret");

        User signedinUser = newUser("admin");
        User user = newUser("user");
        user.setAuthMethod(User.AuthMethod.LDAP);

        boolean threw = false;
        try {
            authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_allowed_ok() throws Exception {
        UserProperties props = newUserProps("user");
        props.setActive(true);

        User signedinUser = newUser("admin");
        User user = newUser("user");
        user.setAuthMethod(User.AuthMethod.DB);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
    }

    @Test(expected = NotFoundException.class)
    public void checkSignedinUserAllowedToAccessUser_nullUser_throwsNotFound() throws Exception {
        authorizationService.checkSignedinUserAllowedToAccessUser(null, newUser("u"));
    }

    @Test
    public void checkSignedinUserAllowedToAccessUser_self_ok() throws Exception {
        User user = newUser("u");
        authorizationService.checkSignedinUserAllowedToAccessUser(user, user);
    }

    @Test
    public void checkSignedinUserAllowedToAccessUser_adminToNonAdmin_ok() throws Exception {
        User signedin = newUser("adminUser");
        signedin.addRole(User.Role.ADMIN);
        User target = newUser("user");

        authorizationService.checkSignedinUserAllowedToAccessUser(target, signedin);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToAccessUser_adminToAdmin_forbidden() throws Exception {
        User signedin = newUser("adminUser");
        signedin.addRole(User.Role.ADMIN);
        User target = newUser("admin2");
        target.addRole(User.Role.ADMIN);

        authorizationService.checkSignedinUserAllowedToAccessUser(target, signedin);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToAccessUser_otherUser_forbidden() throws Exception {
        User signedin = newUser("u1");
        User target = newUser("u2");

        authorizationService.checkSignedinUserAllowedToAccessUser(target, signedin);
    }

    @Test(expected = NotFoundException.class)
    public void checkUserAllowedToAccessApiToken_nullToken_throwsNotFound() throws Exception {
        authorizationService.checkUserAllowedToAccessApiToken(null, newUser("u"));
    }

    @Test
    public void checkUserAllowedToAccessApiToken_adminToNonAdmin_ok() throws Exception {
        User signedin = newUser("adminUser");
        signedin.addRole(User.Role.ADMIN);
        User tokenUser = newUser("user");

        ApiToken token = new ApiToken();
        token.setUser(tokenUser);

        authorizationService.checkUserAllowedToAccessApiToken(token, signedin);
    }

    @Test(expected = ForbiddenException.class)
    public void checkUserAllowedToAccessApiToken_otherUser_forbidden() throws Exception {
        User signedin = newUser("u1");
        User tokenUser = newUser("u2");

        ApiToken token = new ApiToken();
        token.setUser(tokenUser);

        authorizationService.checkUserAllowedToAccessApiToken(token, signedin);
    }

    @Test(expected = NotFoundException.class)
    public void checkUserExists_null_throwsNotFound() throws Exception {
        authorizationService.checkUserExists(null);
    }

    @Test
    public void checkUserExists_user_ok() throws Exception {
        authorizationService.checkUserExists(newUser("u"));
    }
}
