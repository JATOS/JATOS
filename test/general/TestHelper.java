package general;

import com.google.inject.Injector;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.RequestScope;
import models.common.Study;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.Application;
import play.Logger;
import play.api.mvc.RequestHeader;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import play.mvc.Http.RequestBuilder;
import play.test.Helpers;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.route;

/**
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class TestHelper {

    public static final String WWW_EXAMPLE_COM = "www.example.com";

    public static final String BASIC_EXAMPLE_STUDY_ZIP = "test/resources/basic_example_study.zip";

    public static final String BLA_EMAIL = "bla@bla.org";

    public static final String BLA_UPPER_CASE_EMAIL = "BLA@BLA.ORG";

    @Inject
    private Application fakeApplication;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyDao studyDao;

    @Inject
    private StudyService studyService;

    @Inject
    private UserService userService;

    @Inject
    private BatchService batchService;

    @Inject
    private AuthenticationService authenticationService;

    @Inject
    private IOUtils ioUtils;

    @Inject
    private Common common;

    public void removeStudyAssetsRootDir() throws IOException {
        File assetsRoot = new File(Common.getStudyAssetsRootPath());
        if (Objects.requireNonNull(assetsRoot.list()).length > 0) {
            Logger.warn(TestHelper.class.getSimpleName()
                    + ".removeStudyAssetsRootDir: Study assets root directory "
                    + Common.getStudyAssetsRootPath()
                    + " is not empty after finishing testing. This should not happen.");
        }
        FileUtils.deleteDirectory(assetsRoot);
    }

    public void removeAllStudyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getStudyLogsPath()));
    }

    public User getAdmin() {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            return fetchTheLazyOnes(admin);
        });
    }

    /**
     * Creates and persist an DB user if an user with this email doesn't exist already.
     */
    public User createAndPersistUser(String username, String name, String password) {
        return createAndPersistUser(username, name, password, User.AuthMethod.DB, false);
    }

    /**
     * Creates and persist an LDAP user if an user with this email doesn't exist already.
     */
    public User createAndPersistUserLdap(String username, String name, String password, boolean admin) {
        return createAndPersistUser(username, name, password, User.AuthMethod.LDAP, admin);
    }

    /**
     * Creates and persist an OAuth Google user if an user with this email doesn't exist already.
     */
    public User createAndPersistUserOAuthGoogle(String username, String name, String password, boolean admin) {
        return createAndPersistUser(username, name, password, User.AuthMethod.OAUTH_GOOGLE, admin);
    }

    /**
     * Creates and persist an LDAP user if an user with this email doesn't exist already.
     */
    public User createAndPersistUser(String username, String name, String password, User.AuthMethod authMethod,
            boolean admin) {
        return jpaApi.withTransaction(entityManager -> {
            User user = userDao.findByUsername(username);
            if (user == null) {
                user = new User(username, name);
                userService.createAndPersistUser(user, password, admin, authMethod);
            }
            return user;
        });
    }

    public void removeUser(String userEmail) {
        jpaApi.withTransaction(() -> {
            try {
                userService.removeUser(userEmail);
            } catch (ForbiddenException | IOException e) {
                throw new RuntimeException(e);
            } catch (NotFoundException e) {
                // We don't care
            }
        });
    }

    public Study createAndPersistExampleStudyForAdmin(Injector injector) {
        return createAndPersistExampleStudy(injector, UserService.ADMIN_USERNAME);
    }

    public Study createAndPersistExampleStudy(Injector injector, String userEmail) {
        return jpaApi.withTransaction(() -> {
            User user = userDao.findByUsername(userEmail);
            try {
                Study exampleStudy = importExampleStudy(injector);
                studyService.createAndPersistStudy(user, exampleStudy);
                return exampleStudy;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public Study importExampleStudy(Injector injector) throws IOException {
        File studyZip = new File(BASIC_EXAMPLE_STUDY_ZIP);
        File tempUnzippedStudyDir = Files.createTempDirectory("JatosImport_" + UUID.randomUUID()).toFile();
        ZipUtil.unzip(studyZip, tempUnzippedStudyDir);
        File[] studyFileList = ioUtils.findFiles(tempUnzippedStudyDir, "",
                IOUtils.STUDY_FILE_SUFFIX);
        File studyFile = studyFileList[0];
        UploadUnmarshaller<Study> uploadUnmarshaller = injector
                .getInstance(StudyUploadUnmarshaller.class);
        Study importedStudy = uploadUnmarshaller.unmarshalling(studyFile);
        //noinspection ResultOfMethodCallIgnored
        studyFile.delete();

        File[] dirArray = ioUtils.findDirectories(tempUnzippedStudyDir);
        ioUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

        //noinspection ResultOfMethodCallIgnored
        tempUnzippedStudyDir.delete();

        // Every study has a default batch
        importedStudy.addBatch(batchService.createDefaultBatch(importedStudy));
        return importedStudy;
    }

    public void removeAllStudies() {
        jpaApi.withTransaction(() -> studyDao.findAll().forEach(study -> {
            try {
                studyService.removeStudyInclAssets(study, getAdmin());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    /**
     * Our custom ErrorHandler isn't used in test cases:
     * https://github.com/playframework/playframework/issues/2484
     * <p>
     * So this method can be used to catch the RuntimeException and check
     * manually that the correct JatosGuiException was thrown.
     */
    public void assertJatosGuiException(RequestBuilder request,
            int httpStatus, String errorMsg) {
        try {
            route(fakeApplication, request);
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(JatosGuiException.class);
            JatosGuiException jatosGuiException = (JatosGuiException) e
                    .getCause();
            assertThat(jatosGuiException.getSimpleResult().status())
                    .isEqualTo(httpStatus);
            assertThat(jatosGuiException.getMessage()).contains(errorMsg);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T fetchTheLazyOnes(T obj) {
        Hibernate.initialize(obj);
        if (obj instanceof HibernateProxy) {
            obj = (T) ((HibernateProxy) obj).getHibernateLazyInitializer()
                    .getImplementation();
        }
        return obj;
    }

    /**
     * Mocks Play's Http.Context without cookies
     */
    public void mockContext() {
        Cookies cookies = mock(Cookies.class);
        mockContext(cookies);
    }

    /**
     * Mocks Play's Http.Context with one cookie that can be retrieved by
     * cookies.get(name)
     */
    public void mockContext(Cookie cookie) {
        Cookies cookies = mock(Cookies.class);
        when(cookies.get(cookie.name())).thenReturn(cookie);
        mockContext(cookies);
    }

    /**
     * Mocks Play's Http.Context with cookies. The cookies can be retrieved by
     * cookieList.iterator()
     */
    public void mockContext(List<Cookie> cookieList) {
        Cookies cookies = mock(Cookies.class);
        when(cookies.iterator()).thenReturn(cookieList.iterator());
        mockContext(cookies);
    }

    private void mockContext(Cookies cookies) {
        Map<String, String> flashData = Collections.emptyMap();
        Map<String, Object> argData = Collections.emptyMap();
        Long id = 2L;
        RequestHeader header = mock(RequestHeader.class);
        Http.Request request = mock(Http.Request.class);
        when(request.cookies()).thenReturn(cookies);
        when(request.queryString()).thenReturn(null);
        Http.Context context = new Http.Context(id, header, request, flashData, flashData, argData,
                Helpers.contextComponents());
        Http.Context.current.set(context);
    }

    public Http.Session mockSessionCookieAndCache(User user) {
        Http.Session session = new Http.Session(new HashMap<>());
        authenticationService.writeSessionCookieAndSessionCache(session, user.getUsername(), WWW_EXAMPLE_COM);
        return session;
    }

    public void defineLoggedInUser(User user) {
        mockContext();
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, user);
    }

    /**
     * Use reflection to set up LDAP in Common
     */
    public void setupLdap(String ldapUrl, String ldapBasedn) {
        try {
            Field ldapUrlField = Common.class.getDeclaredField("ldapUrl");
            ldapUrlField.setAccessible(true);
            ldapUrlField.set(common, ldapUrl);
            Field ldapBasednField = Common.class.getDeclaredField("ldapBasedn");
            ldapBasednField.setAccessible(true);
            ldapBasednField.set(common, ldapBasedn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
