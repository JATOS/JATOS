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
import java.nio.file.Files;
import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.route;

/**
 * @author Kristian Lange (2017)
 */
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
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            return fetchTheLazyOnes(admin);
        });
    }

    /**
     * Creates and persist user if an user with this email doesn't exist
     * already.
     */
    public User createAndPersistUser(String email, String name,
            String password) {
        return jpaApi.withTransaction(entityManager -> {
            User user = userDao.findByEmail(email);
            if (user == null) {
                user = new User(email, name);
                userService.createAndPersistUser(user, password, false);
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
        return createAndPersistExampleStudy(injector, UserService.ADMIN_EMAIL);
    }

    public Study createAndPersistExampleStudy(Injector injector, String userEmail) {
        return jpaApi.withTransaction(() -> {
            User user = userDao.findByEmail(userEmail);
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
        File tempUnzippedStudyDir = Files.createTempDirectory(
                "JatosImport_" + UUID.randomUUID().toString()).toFile();
        ZipUtil.unzip(studyZip, tempUnzippedStudyDir);
        File[] studyFileList = ioUtils.findFiles(tempUnzippedStudyDir, "",
                IOUtils.STUDY_FILE_SUFFIX);
        File studyFile = studyFileList[0];
        UploadUnmarshaller<Study> uploadUnmarshaller = injector
                .getInstance(StudyUploadUnmarshaller.class);
        Study importedStudy = uploadUnmarshaller.unmarshalling(studyFile);
        studyFile.delete();

        File[] dirArray = ioUtils.findDirectories(tempUnzippedStudyDir);
        ioUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

        tempUnzippedStudyDir.delete();

        // Every study has a default batch
        importedStudy.addBatch(batchService.createDefaultBatch(importedStudy));
        return importedStudy;
    }

    public void removeStudy(Long studyId) {
        jpaApi.withTransaction(() -> {
            try {
                Study study = studyDao.findById(studyId);
                if (study != null) {
                    studyService.removeStudyInclAssets(study);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void removeAllStudies() {
        jpaApi.withTransaction(() -> studyDao.findAll().forEach(study -> {
            try {
                studyService.removeStudyInclAssets(study);
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
        mockContext(cookies, null);
    }

    /**
     * Mocks Play's Http.Context with one cookie that can be retrieved by
     * cookies.get(name)
     */
    public void mockContext(Cookie cookie) {
        Cookies cookies = mock(Cookies.class);
        when(cookies.get(cookie.name())).thenReturn(cookie);
        mockContext(cookies, null);
    }

    /**
     * Mocks Play's Http.Context with cookies. The cookies can be retrieved by
     * cookieList.iterator()
     */
    public void mockContext(List<Cookie> cookieList) {
        Cookies cookies = mock(Cookies.class);
        when(cookies.iterator()).thenReturn(cookieList.iterator());
        mockContext(cookies, null);
    }

    /**
     * Mocks Play's Http.Context with URL query string parameters
     */
    public void mockContext(Map<String, String[]> queryString) {
        Cookies cookies = mock(Cookies.class);
        mockContext(cookies, queryString);
    }

    private void mockContext(Cookies cookies, Map<String, String[]> queryString) {
        Map<String, String> flashData = Collections.emptyMap();
        Map<String, Object> argData = Collections.emptyMap();
        Long id = 2L;
        RequestHeader header = mock(RequestHeader.class);
        Http.Request request = mock(Http.Request.class);
        when(request.cookies()).thenReturn(cookies);
        when(request.queryString()).thenReturn(queryString);
        Http.Context context = new Http.Context(id, header, request, flashData, flashData, argData,
                Helpers.contextComponents());
        Http.Context.current.set(context);
    }

    public Http.Session mockSessionCookieandCache(User user) {
        Http.Session session = new Http.Session(new HashMap<>());
        authenticationService.writeSessionCookieAndUserSessionCache(session,
                user.getEmail(), WWW_EXAMPLE_COM);
        return session;
    }

    public void defineLoggedInUser(User user) {
        mockContext();
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, user);
    }

}
