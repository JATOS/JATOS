package services.gui;

import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.Common;
import http.common.Http;
import http.common.Http.Context;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.test.Helpers;
import testutils.gui.JPAMocker;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static services.gui.UserService.ADMIN_USERNAME;

/**
 * Unit tests for UserService.
 */
public class UserServiceTest {

    private StudyService studyService;
    private UserDao userDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;

    private UserService userService;

    @Before
    public void setUp() {
        studyService = mock(StudyService.class);
        userDao = mock(UserDao.class);
        studyDao = mock(StudyDao.class);
        workerDao = mock(WorkerDao.class);

        userService = new UserService(studyService, userDao, studyDao, workerDao);

        EntityManager entityManager = Mockito.mock(EntityManager.class);
        JPAMocker.mockDaoTransactions(entityManager, userDao, studyDao, workerDao);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @Test
    public void retrieveUser_returnsUser() {
        User u = new User("foo@foo.org", "Foo", "foo@foo.org");
        when(userDao.findByUsername("foo@foo.org")).thenReturn(u);

        User got = userService.retrieveUser("foo@foo.org");
        assertThat(got).isEqualTo(u);
    }

    @Test(expected = NotFoundException.class)
    public void retrieveUser_notFound_throws() {
        when(userDao.findByUsername("missing")).thenReturn(null);
        userService.retrieveUser("missing");
    }

    @Test
    public void createAndPersistUser_db_setsHashRolesWorker_andPersists() {
        User u = new User("foo@foo.org", "Foo Bar", "foo@foo.org");

        userService.createAndPersistUser(u, "secret", true, AuthMethod.DB);

        // Password hash set for DB auth
        assertThat(u.getPasswordHash()).isNotNull();
        // Roles contain USER and ADMIN
        assertThat(u.getRoleList()).contains(Role.USER, Role.ADMIN);
        // Worker created and set
        JatosWorker w = u.getWorker();
        assertThat(w).isNotNull();
        assertThat(w.getUser()).isEqualTo(u);
        // DAO interactions
        verify(workerDao, times(1)).persist(any(JatosWorker.class));
        verify(userDao, times(1)).persist(eq(u));
    }

    @Test
    public void createAndPersistUser_nonDb_noPasswordHash() {
        User u = new User("foo@foo.org", "Foo Bar", "foo@foo.org");
        userService.createAndPersistUser(u, "secret", false, AuthMethod.LDAP);
        assertThat(u.getPasswordHash()).isNull();
        assertThat(u.getRoleList()).containsOnly(Role.USER);
    }

    @Test
    public void updatePassword_setsHash_andUpdates() {
        User u = new User("foo@foo.org", "Foo", "foo@foo.org");
        userService.updatePassword(u, "newPass");
        assertThat(u.getPasswordHash()).isNotEmpty();
        verify(userDao, times(1)).merge(eq(u));
    }

    @Test
    public void toggleActive_success_updates() {
        User target = new User("target@ex.org", "Target", "target@ex.org");
        when(userDao.findByUsername("target@ex.org")).thenReturn(target);

        Context.current().args().put(SIGNEDIN_USER, new User("other@ex.org", "Other", "other@ex.org"));

        userService.toggleActive("target@ex.org", false);
        assertThat(target.isActive()).isFalse();
        verify(userDao).merge(target);
    }

    @Test(expected = ForbiddenException.class)
    public void toggleActive_self_forbidden() {
        User self = new User("me@ex.org", "Me", "me@ex.org");
        when(userDao.findByUsername("me@ex.org")).thenReturn(self);
        Context.current().args().put(SIGNEDIN_USER, self);
        userService.toggleActive("me@ex.org", false);
    }

    @Test(expected = ForbiddenException.class)
    public void toggleActive_admin_forbidden() {
        User admin = new User(ADMIN_USERNAME, "Admin", "admin@ex.org");
        when(userDao.findByUsername(ADMIN_USERNAME)).thenReturn(admin);
        Context.current().args().put(SIGNEDIN_USER, new User("other@ex.org", "Other", "other@ex.org"));
        userService.toggleActive(ADMIN_USERNAME, false);
    }

    @Test
    public void changeSuperuserRole_allowed_addAndRemove_updatesAndReturns() {
        setCommonSuperuserAllowed(true);
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        Set<Role> afterAdd = userService.changeSuperuserRole("foo@ex.org", true);
        assertThat(afterAdd).containsOnly(Role.USER, Role.SUPERUSER);
        assertThat(u.isSuperuser()).isTrue();
        verify(userDao, times(1)).merge(u);

        Set<Role> afterRemove = userService.changeSuperuserRole("foo@ex.org", false);
        assertThat(afterRemove).containsOnly(Role.USER);
        assertThat(u.isSuperuser()).isFalse();
        verify(userDao, times(2)).merge(u);
    }

    @Test(expected = ForbiddenException.class)
    public void changeSuperuserRole_notAllowed_forbidden() {
        setCommonSuperuserAllowed(false);
        userService.changeSuperuserRole("any", true);
    }

    @Test
    public void changeAdminRole_addAndRemove_andReturnFlag() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        Context.current().args().put(SIGNEDIN_USER, new User("other@ex.org", "Other", "other@ex.org"));

        Set<Role> afterAdd = userService.changeAdminRole("foo@ex.org", true);
        assertThat(afterAdd).containsOnly(Role.USER, Role.ADMIN);
        assertThat(u.isAdmin()).isTrue();

        Set<Role> afterRemove = userService.changeAdminRole("foo@ex.org", false);
        assertThat(afterRemove).containsOnly(Role.USER);
        assertThat(u.isAdmin()).isFalse();
    }

    @Test(expected = ForbiddenException.class)
    public void changeAdminRole_selfRemoval_forbidden() {
        User self = new User("me@ex.org", "Me", "me@ex.org");
        when(userDao.findByUsername("me@ex.org")).thenReturn(self);
        Context.current().args().put(SIGNEDIN_USER, self);
        userService.changeAdminRole("me@ex.org", false);
    }

    @Test(expected = ForbiddenException.class)
    public void changeAdminRole_adminUser_forbidden() {
        User admin = new User(ADMIN_USERNAME, "Admin", "admin@ex.org");
        when(userDao.findByUsername(ADMIN_USERNAME)).thenReturn(admin);
        Context.current().args().put(SIGNEDIN_USER, new User("other@ex.org", "Other", "other@ex.org"));
        userService.changeAdminRole(ADMIN_USERNAME, false);
    }

    @Test
    public void setLastSignin_setsTimestamp_andUpdatesViaJPA() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        userService.setLastSignin("foo@ex.org");

        assertThat(u.getLastLogin()).isNotNull();
        verify(userDao, times(1)).merge(u);
    }

    @Test
    public void setLastSeen_setsTimestamp_andUpdates() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        userService.setLastSeen(u);
        Timestamp ts = u.getLastSeen();
        assertThat(ts).isNotNull();
        verify(userDao).merge(u);
    }

    @Test(expected = ForbiddenException.class)
    public void removeUser_admin_forbidden() {
        User admin = new User(ADMIN_USERNAME, "Admin", "admin@ex.org");
        admin.setId(42L);
        when(userDao.findByUsername(ADMIN_USERNAME)).thenReturn(admin);
        when(userDao.findById(42L)).thenReturn(admin);

        userService.removeUser(ADMIN_USERNAME);
    }

    @Test
    public void removeUser_removesStudiesTokensAndUser() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        u.setId(42L);
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);
        when(userDao.findById(42L)).thenReturn(u);

        Study s = new Study();
        // Simulate that user is member and sole member of the study
        s.addUser(u);

        userService.removeUser("foo@ex.org");

        // On sole membership: removeStudyInclAssets(study, user) was called
        verify(studyService, times(1)).removeStudyInclAssets(eq(s));
        // Finally, user removed
        verify(userDao, times(1)).remove(u);
    }

    @Test
    public void removeUser_multipleMembers_updatesStudy() {
        User u = new User("foo2@ex.org", "Foo2", "foo2@ex.org");
        u.setId(42L);
        when(userDao.findByUsername("foo2@ex.org")).thenReturn(u);
        when(userDao.findById(42L)).thenReturn(u);

        Study s = new Study();
        User other = new User("other@ex.org", "Other", "other@ex.org");
        s.addAllUsers(List.of(u, other));

        userService.removeUser("foo2@ex.org");

        // For multi-member study, service removes user from study and updates it
        assertThat(s.getUserList().contains(u)).isFalse();
        verify(studyDao, times(1)).merge(eq(s));
        verify(userDao, times(1)).remove(u);
    }

    private static void setCommonSuperuserAllowed(boolean value) {
        try {
            Field f = Common.class.getDeclaredField("userRoleAllowSuperuser");
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            fail("Failed to set Common.userRoleAllowSuperuser via reflection: " + e);
        }
    }
}
