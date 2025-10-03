package services.gui;

import auth.gui.AuthService;
import daos.common.ApiTokenDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import org.junit.Before;
import org.junit.Test;
import play.db.jpa.JPAApi;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Supplier;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
public class UserServiceTest {

    private StudyService studyService;
    private AuthService authService;
    private UserDao userDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;
    private ApiTokenDao apiTokenDao;

    private UserService userService;

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() {
        studyService = mock(StudyService.class);
        authService = mock(AuthService.class);
        userDao = mock(UserDao.class);
        studyDao = mock(StudyDao.class);
        workerDao = mock(WorkerDao.class);
        apiTokenDao = mock(ApiTokenDao.class);

        JPAApi jpaApi = mock(JPAApi.class);
        // Mock JPAApi.withTransaction(Supplier<R>) to execute the supplier
        when(jpaApi.withTransaction(any(Supplier.class))).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        // Mock JPAApi.withTransaction(Runnable) to run the runnable
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(jpaApi).withTransaction(any(Runnable.class));

        userService = new UserService(studyService, authService, userDao, studyDao, workerDao, apiTokenDao, jpaApi);
    }

    @Test
    public void retrieveUser_returnsUser() throws NotFoundException {
        User u = new User("foo@foo.org", "Foo", "foo@foo.org");
        when(userDao.findByUsername("foo@foo.org")).thenReturn(u);

        User got = userService.retrieveUser("foo@foo.org");
        assertThat(got).isEqualTo(u);
    }

    @Test(expected = NotFoundException.class)
    public void retrieveUser_notFound_throws() throws NotFoundException {
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
        verify(workerDao, times(1)).create(any(JatosWorker.class));
        verify(userDao, times(1)).create(eq(u));
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
        verify(userDao, times(1)).update(eq(u));
    }

    @Test
    public void toggleActive_success_updates() throws Exception {
        User target = new User("target@ex.org", "Target", "target@ex.org");
        when(userDao.findByUsername("target@ex.org")).thenReturn(target);
        when(authService.getSignedinUser()).thenReturn(new User("other@ex.org", "Other", "other@ex.org"));

        userService.toggleActive("target@ex.org", false);
        assertThat(target.isActive()).isFalse();
        verify(userDao).update(target);
    }

    @Test(expected = ForbiddenException.class)
    public void toggleActive_self_forbidden() throws Exception {
        User self = new User("me@ex.org", "Me", "me@ex.org");
        when(userDao.findByUsername("me@ex.org")).thenReturn(self);
        when(authService.getSignedinUser()).thenReturn(self);
        userService.toggleActive("me@ex.org", false);
    }

    @Test(expected = ForbiddenException.class)
    public void toggleActive_admin_forbidden() throws Exception {
        User admin = new User(UserService.ADMIN_USERNAME, "Admin", "admin@ex.org");
        when(userDao.findByUsername(UserService.ADMIN_USERNAME)).thenReturn(admin);
        when(authService.getSignedinUser()).thenReturn(new User("other@ex.org", "Other", "other@ex.org"));
        userService.toggleActive(UserService.ADMIN_USERNAME, false);
    }

    @Test
    public void changeSuperuserRole_allowed_addAndRemove_updatesAndReturns() throws Exception {
        setCommonSuperuserAllowed(true);
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        boolean afterAdd = userService.changeSuperuserRole("foo@ex.org", true);
        assertThat(afterAdd).isTrue();
        assertThat(u.isSuperuser()).isTrue();
        verify(userDao, times(1)).update(u);

        boolean afterRemove = userService.changeSuperuserRole("foo@ex.org", false);
        assertThat(afterRemove).isFalse();
        assertThat(u.isSuperuser()).isFalse();
        verify(userDao, times(2)).update(u);
    }

    @Test(expected = ForbiddenException.class)
    public void changeSuperuserRole_notAllowed_forbidden() throws Exception {
        setCommonSuperuserAllowed(false);
        userService.changeSuperuserRole("any", true);
    }

    @Test
    public void changeAdminRole_addAndRemove_andReturnFlag() throws Exception {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);
        when(authService.getSignedinUser()).thenReturn(new User("other@ex.org", "Other", "other@ex.org"));

        boolean afterAdd = userService.changeAdminRole("foo@ex.org", true);
        assertThat(afterAdd).isTrue();
        assertThat(u.isAdmin()).isTrue();

        boolean afterRemove = userService.changeAdminRole("foo@ex.org", false);
        assertThat(afterRemove).isFalse();
        assertThat(u.isAdmin()).isFalse();
    }

    @Test(expected = ForbiddenException.class)
    public void changeAdminRole_selfRemoval_forbidden() throws Exception {
        User self = new User("me@ex.org", "Me", "me@ex.org");
        when(userDao.findByUsername("me@ex.org")).thenReturn(self);
        when(authService.getSignedinUser()).thenReturn(self);
        userService.changeAdminRole("me@ex.org", false);
    }

    @Test(expected = ForbiddenException.class)
    public void changeAdminRole_adminUser_forbidden() throws Exception {
        User admin = new User(UserService.ADMIN_USERNAME, "Admin", "admin@ex.org");
        when(userDao.findByUsername(UserService.ADMIN_USERNAME)).thenReturn(admin);
        when(authService.getSignedinUser()).thenReturn(new User("other@ex.org", "Other", "other@ex.org"));
        userService.changeAdminRole(UserService.ADMIN_USERNAME, false);
    }

    @Test
    public void setLastSignin_setsTimestamp_andUpdatesViaJPA() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        userService.setLastSignin("foo@ex.org");

        assertThat(u.getLastLogin()).isNotNull();
        verify(userDao, times(1)).update(u);
    }

    @Test
    public void setLastSeen_setsTimestamp_andUpdates() {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        userService.setLastSeen(u);
        Timestamp ts = u.getLastSeen();
        assertThat(ts).isNotNull();
        verify(userDao).update(u);
    }

    @Test(expected = ForbiddenException.class)
    public void removeUser_admin_forbidden() throws Exception {
        // Ensure retrieveUser finds the admin user so the Forbidden check is reached
        when(userDao.findByUsername(UserService.ADMIN_USERNAME))
                .thenReturn(new User(UserService.ADMIN_USERNAME, "Admin", "admin@ex.org"));
        userService.removeUser(UserService.ADMIN_USERNAME);
    }

    @Test
    public void removeUser_removesStudiesTokensAndUser() throws Exception {
        User u = new User("foo@ex.org", "Foo", "foo@ex.org");
        when(userDao.findByUsername("foo@ex.org")).thenReturn(u);

        Study s = new Study();
        // Simulate that user is member and sole member of the study
        s.setUserList(new HashSet<>(Collections.singletonList(u)));
        u.setStudyList(new HashSet<>(Collections.singletonList(s)));

        // Make apiTokenDao return two tokens to be removed (we only care about calls)
        when(apiTokenDao.findByUser(u)).thenReturn(Collections.emptyList());

        userService.removeUser("foo@ex.org");

        // On sole membership: removeStudyInclAssets(study, user) was called
        verify(studyService, times(1)).removeStudyInclAssets(eq(s), eq(u));
        // API tokens removed (find called and iteration attempted)
        verify(apiTokenDao, times(1)).findByUser(u);
        // Finally user removed
        verify(userDao, times(1)).remove(u);
    }

    @Test
    public void removeUser_multipleMembers_updatesStudy() throws Exception {
        User u = new User("foo2@ex.org", "Foo2", "foo2@ex.org");
        when(userDao.findByUsername("foo2@ex.org")).thenReturn(u);

        Study s = new Study();
        User other = new User("other@ex.org", "Other", "other@ex.org");
        s.setUserList(new HashSet<>(Arrays.asList(u, other)));
        u.setStudyList(new HashSet<>(Collections.singletonList(s)));

        when(apiTokenDao.findByUser(u)).thenReturn(Collections.emptyList());

        userService.removeUser("foo2@ex.org");

        // For multi-member study, service removes user from study and updates it
        assertThat(s.getUserList().contains(u)).isFalse();
        verify(studyDao, times(1)).update(eq(s));
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
