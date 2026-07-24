package services.gui;

import daos.common.StudyDao;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.Common;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AuthorizationServiceTest {

    private StudyDao studyDao;
    private AuthorizationService authorizationService;
    private MockedStatic<Common> commonStatic;

    @Before
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void setup() {
        studyDao = mock(StudyDao.class);
        authorizationService = new AuthorizationService(studyDao);

        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::isUserRoleAllowSuperuser).thenReturn(false);
    }

    @After
    public void tearDown() {
        if (commonStatic != null) {
            commonStatic.close();
        }
    }

    @Test
    public void isAllowedSuperuser_returnsFalse_whenSuperuserRoleIsDisabled() {
        User user = user("superuser", Role.SUPERUSER);

        assertThat(authorizationService.isAllowedSuperuser(user)).isFalse();
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void isAllowedSuperuser_returnsTrue_whenSuperuserRoleIsEnabled() {
        commonStatic.when(Common::isUserRoleAllowSuperuser).thenReturn(true);
        User user = user("superuser", Role.SUPERUSER);

        assertThat(authorizationService.isAllowedSuperuser(user)).isTrue();
    }

    @Test
    public void isMemberOrSuperuser_returnsTrue_whenUserIsStudyMember() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(true);

        assertThat(authorizationService.isMemberOrSuperuser(study, user)).isTrue();
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void isMemberOrSuperuser_returnsTrue_whenUserIsAllowedSuperuser() {
        commonStatic.when(Common::isUserRoleAllowSuperuser).thenReturn(true);
        Study study = study(1L);
        User user = user("superuser", Role.SUPERUSER);
        when(studyDao.hasUser(study, user)).thenReturn(false);

        assertThat(authorizationService.isMemberOrSuperuser(study, user)).isTrue();
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessStudy_throwsNotFound_whenStudyIsNull() {
        authorizationService.canUserAccessStudy(null, user("user", Role.USER));
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessStudy_throwsForbidden_whenUserIsNotMember() {
        Study study = study(1L);
        User user = user("user", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(false);

        authorizationService.canUserAccessStudy(study, user);
    }

    @Test
    public void canUserAccessStudy_allowsMember() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(true);

        authorizationService.canUserAccessStudy(study, user);

        verify(studyDao).hasUser(study, user);
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessStudy_throwsForbidden_whenStudyIsLockedAndUnlockedStudyIsRequired() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(true);
        when(studyDao.isLocked(1L)).thenReturn(true);

        authorizationService.canUserAccessStudy(study, user, true);
    }

    @Test
    public void canUserAccessStudy_allowsLockedStudy_whenUnlockedStudyIsNotRequired() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(true);
        when(studyDao.isLocked(1L)).thenReturn(true);

        authorizationService.canUserAccessStudy(study, user, false);

        verify(studyDao, never()).isLocked(1L);
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessComponent_throwsNotFound_whenComponentIsNull() {
        authorizationService.canUserAccessComponent(null, user("user", Role.USER));
    }

    @Test
    public void canUserAccessComponent_allowsMemberOfComponentsStudy() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        Component component = mock(Component.class);
        when(component.getStudy()).thenReturn(study);
        when(studyDao.hasUser(study, user)).thenReturn(true);

        authorizationService.canUserAccessComponent(component, user);

        verify(studyDao).hasUser(study, user);
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessComponent_throwsForbidden_whenStudyIsLockedAndUnlockedStudyIsRequired() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        Component component = mock(Component.class);
        when(component.getStudy()).thenReturn(study);
        when(studyDao.hasUser(study, user)).thenReturn(true);
        when(studyDao.isLocked(1L)).thenReturn(true);

        authorizationService.canUserAccessComponent(component, user, true);
    }

    @Test(expected = NotFoundException.class)
    public void canUserAccessBatch_throwsNotFound_whenBatchIsNull() {
        authorizationService.canUserAccessBatch(null, user("user", Role.USER));
    }

    @Test
    public void canUserAccessBatch_allowsMemberOfBatchStudy() {
        Study study = study(1L);
        User user = user("member", Role.USER);
        Batch batch = mock(Batch.class);
        when(batch.getStudy()).thenReturn(study);
        when(studyDao.hasUser(study, user)).thenReturn(true);

        authorizationService.canUserAccessBatch(batch, user);

        verify(studyDao).hasUser(study, user);
    }

    @Test
    public void canUserAccessStudyOrAdmin_allowsAdminEvenIfNotMember() {
        Study study = study(1L);
        User admin = user("admin-user", Role.ADMIN);
        when(studyDao.hasUser(study, admin)).thenReturn(false);

        authorizationService.canUserAccessStudyOrAdmin(study, admin);
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessStudyOrAdmin_throwsForbidden_whenUserIsNeitherMemberNorAdmin() {
        Study study = study(1L);
        User user = user("user", Role.USER);
        when(studyDao.hasUser(study, user)).thenReturn(false);

        authorizationService.canUserAccessStudyOrAdmin(study, user);
    }

    @Test(expected = NotFoundException.class)
    public void checkAdminOrSelf_throwsNotFound_whenTargetUserIsNull() {
        User user = null;
        authorizationService.checkAdminOrSelf(user("signedin", Role.USER), user);
    }

    @Test
    public void checkAdminOrSelf_allowsSameUser() {
        User signedinUser = user("user", Role.USER);

        authorizationService.checkAdminOrSelf(signedinUser, signedinUser);
    }

    @Test
    public void checkAdminOrSelf_allowsAdmin() {
        User admin = user("admin-user", Role.ADMIN);
        User otherUser = user("other", Role.USER);

        authorizationService.checkAdminOrSelf(admin, otherUser);
    }

    @Test(expected = ForbiddenException.class)
    public void checkAdminOrSelf_throwsForbidden_whenUserIsNeitherAdminNorSelf() {
        User signedinUser = user("signedin", Role.USER);
        User otherUser = user("other", Role.USER);

        authorizationService.checkAdminOrSelf(signedinUser, otherUser);
    }

    @Test
    public void checkAuthMethodIsDbOrLdap_allowsDbUser() {
        User user = user("db-user", Role.USER);
        user.setAuthMethod(AuthMethod.DB);

        authorizationService.checkAuthMethodIsDbOrLdap(user);
    }

    @Test
    public void checkAuthMethodIsDbOrLdap_allowsLdapUser() {
        User user = user("ldap-user", Role.USER);
        user.setAuthMethod(AuthMethod.LDAP);

        authorizationService.checkAuthMethodIsDbOrLdap(user);
    }

    @Test(expected = ForbiddenException.class)
    public void checkAuthMethodIsDbOrLdap_throwsForbidden_forOidcUser() {
        User user = user("oidc-user", Role.USER);
        user.setAuthMethod(AuthMethod.OIDC);

        authorizationService.checkAuthMethodIsDbOrLdap(user);
    }

    @Test
    public void checkAuthMethodIsDbOrLdap_allowsDbNewUserProperties() {
        NewUserProperties props = new NewUserProperties();
        props.setAuthMethod(AuthMethod.DB);

        authorizationService.checkAuthMethodIsDbOrLdap(props);
    }

    @Test(expected = ForbiddenException.class)
    public void checkAuthMethodIsDbOrLdap_throwsForbidden_forOidcNewUserProperties() {
        NewUserProperties props = new NewUserProperties();
        props.setAuthMethod(AuthMethod.OIDC);

        authorizationService.checkAuthMethodIsDbOrLdap(props);
    }

    @Test(expected = NotFoundException.class)
    public void checkNotUserAdmin_throwsNotFound_whenUserIsNull() {
        authorizationService.checkNotUserAdmin(null);
    }

    @Test(expected = ForbiddenException.class)
    public void checkNotUserAdmin_throwsForbidden_forAdminUsername() {
        authorizationService.checkNotUserAdmin(user(UserService.ADMIN_USERNAME, Role.ADMIN));
    }

    @Test
    public void checkNotUserAdmin_allowsNonAdminUsername() {
        authorizationService.checkNotUserAdmin(user("regular-user", Role.USER));
    }

    @Test(expected = NotFoundException.class)
    public void checkNotYourself_throwsNotFound_whenTargetUserIsNull() {
        authorizationService.checkNotYourself(userWithId("signedin", 1L), null);
    }

    @Test(expected = ForbiddenException.class)
    public void checkNotYourself_throwsForbidden_whenSameId() {
        authorizationService.checkNotYourself(userWithId("signedin", 1L), userWithId("other", 1L));
    }

    @Test
    public void checkNotYourself_allowsDifferentId() {
        authorizationService.checkNotYourself(userWithId("signedin", 1L), userWithId("other", 2L));
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToChangeUser_forbidsNonAdminUserChangingAdminPassword() {
        UserProperties props = new UserProperties();
        props.setUsername(UserService.ADMIN_USERNAME);
        props.setPassword("new-password");
        props.setActive(true);

        User signedinUser = user("other-admin", Role.ADMIN);
        User targetUser = user(UserService.ADMIN_USERNAME, Role.ADMIN);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, targetUser);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToChangeUser_forbidsDeactivatingAdminUser() {
        UserProperties props = new UserProperties();
        props.setUsername(UserService.ADMIN_USERNAME);
        props.setActive(false);

        User signedinUser = user(UserService.ADMIN_USERNAME, Role.ADMIN);
        User targetUser = user(UserService.ADMIN_USERNAME, Role.ADMIN);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, targetUser);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToChangeUser_forbidsUserDeactivatingThemselves() {
        UserProperties props = new UserProperties();
        props.setUsername("user");
        props.setActive(false);

        User signedinUser = user("user", Role.USER);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, signedinUser);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToChangeUser_forbidsChangingLdapPassword() {
        UserProperties props = new UserProperties();
        props.setUsername("ldap-user");
        props.setPassword("new-password");
        props.setActive(true);

        User signedinUser = user("ldap-user", Role.USER);
        User targetUser = user("ldap-user", Role.USER);
        targetUser.setAuthMethod(AuthMethod.LDAP);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, targetUser);
    }

    @Test
    public void checkSignedinUserAllowedToChangeUser_allowsRegularActiveDbUserChange() {
        UserProperties props = new UserProperties();
        props.setUsername("user");
        props.setActive(true);

        User signedinUser = user("user", Role.USER);
        User targetUser = user("user", Role.USER);
        targetUser.setAuthMethod(AuthMethod.DB);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, targetUser);
    }

    @Test(expected = NotFoundException.class)
    public void checkSignedinUserAllowedToAccessUser_throwsNotFound_whenTargetUserIsNull() {
        authorizationService.checkSignedinUserAllowedToAccessUser(null, user("signedin", Role.USER));
    }

    @Test
    public void checkSignedinUserAllowedToAccessUser_allowsSelf() {
        User signedinUser = user("user", Role.USER);

        authorizationService.checkSignedinUserAllowedToAccessUser(signedinUser, signedinUser);
    }

    @Test
    public void checkSignedinUserAllowedToAccessUser_allowsAdminAccessingNonAdminUser() {
        User admin = user("admin-user", Role.ADMIN);
        User targetUser = user("target", Role.USER);

        authorizationService.checkSignedinUserAllowedToAccessUser(targetUser, admin);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToAccessUser_forbidsAdminAccessingAdminUser() {
        User admin = user("admin-user", Role.ADMIN);
        User targetAdmin = user("target-admin", Role.ADMIN);

        authorizationService.checkSignedinUserAllowedToAccessUser(targetAdmin, admin);
    }

    @Test(expected = ForbiddenException.class)
    public void checkSignedinUserAllowedToAccessUser_forbidsRegularUserAccessingOtherUser() {
        User signedinUser = user("signedin", Role.USER);
        User targetUser = user("target", Role.USER);

        authorizationService.checkSignedinUserAllowedToAccessUser(targetUser, signedinUser);
    }

    @Test(expected = NotFoundException.class)
    public void checkUserExists_throwsNotFound_whenUserIsNull() {
        authorizationService.checkUserExists(null);
    }

    @Test
    public void checkUserExists_allowsExistingUser() {
        authorizationService.checkUserExists(user("user", Role.USER));
    }

    @Test(expected = ForbiddenException.class)
    public void canUserAccessWorker_throwsForbidden_whenWorkerIsNotInUsersStudies() {
        User user = user("user", Role.USER);
        models.common.workers.Worker worker = mock(models.common.workers.Worker.class);
        when(studyDao.findAllByUser(user)).thenReturn(Collections.emptyList());

        authorizationService.canUserAccessWorker(user, worker);
    }

    @SuppressWarnings("SameParameterValue")
    private static Study study(Long id) {
        Study study = new Study();
        study.setId(id);
        study.setTitle("Study " + id);
        return study;
    }

    private static User user(String username, Role role) {
        User user = new User(username, username, username + "@example.org", role);
        user.setAuthMethod(AuthMethod.DB);
        return user;
    }

    private static User userWithId(String username, Long id) {
        User user = user(username, Role.USER);
        user.setId(id);
        return user;
    }
}