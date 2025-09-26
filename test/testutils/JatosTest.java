package testutils;

import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.NotFoundException;
import general.common.Common;
import models.common.Study;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Files.TemporaryFileCreator;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.ApiTokenService;
import services.gui.StudyService;
import services.gui.UserService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static play.test.Helpers.*;

/**
 * Parent class for all tests that need the JATOS application running.
 *
 * @author Kristian Lange
 */
public class JatosTest {

    public static final String TEST_RESOURCES_POTATO_COMPASS_JZIP = "/test/resources/potato_compass.jzip";

    @Inject
    protected Application application;

    @Inject
    protected JPAApi jpaApi;

    protected User admin;

    protected String apiToken;

    @Before
    public void startApp() {
        application = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Guice.createInjector(builder.applicationModule()).injectMembers(this);

        Helpers.start(application);

        admin = getAdmin();
        apiToken = createApiToken(admin);
    }

    @After
    public void stopApp() throws Exception {
        dropDatabase(); // Remove studies because H2 doesn't get cleared between tests

        Helpers.stop(application);

        removeAllStudyAssets();
        removeAllResultUploads();
        removeAllStudyLogs();
        removeAllLogs();
    }

    public Long importExampleStudy() {
        TemporaryFileCreator temporaryFileCreator = application.injector().instanceOf(TemporaryFileCreator.class);
        Materializer materializer = application.injector().instanceOf(Materializer.class);
        Path studyPath = Paths.get(Common.getBasepath(), TEST_RESOURCES_POTATO_COMPASS_JZIP);
        Source<ByteString, CompletionStage<IOResult>> source = FileIO.fromPath(studyPath);
        Http.MultipartFormData.FilePart<Source<ByteString, ?>> part = new Http.MultipartFormData.FilePart<>("study", "filename", "text/plain", source);
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(POST)
                .header("Authorization", "Bearer " + apiToken)
                .bodyRaw(Collections.singletonList(part), temporaryFileCreator, materializer)
                .uri("/jatos/api/v1/study?keepProperties=false&keepAssets=false&keepCurrentAssetsName=false&renameAssets=false");

        Result result = route(application, request);
        JsonNode content = Json.parse(contentAsString(result));
        Long studyId = content.get("id").asLong();
        return studyId;
    }

    public User getAdmin() {
        UserDao userDao = application.injector().instanceOf(UserDao.class);
        return jpaApi.withTransaction((em) -> {
            User user = userDao.findByUsername(UserService.ADMIN_USERNAME);
            utils.common.Helpers.initializeAndUnproxy(user.getStudyList());
            return user;
        });
    }

    public User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setName("Foo Bar");
        UserService userService = application.injector().instanceOf(UserService.class);
        jpaApi.withTransaction(em -> {
            userService.createAndPersistUser(user, "fooPassword", false, User.AuthMethod.DB);
        });
        return user;
    }

    public String createApiToken(User user) {
        ApiTokenService apiTokenService = application.injector().instanceOf(ApiTokenService.class);
        return jpaApi.withTransaction((em) -> {
            return apiTokenService.create(user, "test-token", 0);
        });
    }

    public Study getStudy(Long id) {
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        return jpaApi.withTransaction((em) -> {
            return studyDao.findById(id);
        });
    }

    public Study importAndGetExampleStudy() {
        Long studyId = importExampleStudy();
        return getStudy(studyId);
    }

    public void dropDatabase() {
        jpaApi.withTransaction(em -> {
            em.createNativeQuery("DROP ALL OBJECTS").executeUpdate();
            return null;
        });
    }

    /**
     * Remove all studies in the database and delete their study assets and result files
     */
    public void removeAllStudies() {
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        StudyService studyService = application.injector().instanceOf(StudyService.class);
        jpaApi.withTransaction((em) -> {
            studyDao.findAll().forEach(unchecked((study) -> studyService.removeStudyInclAssets(study, getAdmin())));
        });
    }

    public void removeUser(String username) {
        UserService userService = application.injector().instanceOf(UserService.class);
        jpaApi.withTransaction(unchecked((em) -> {
            try {
                userService.removeUser(username);
            } catch (NotFoundException e) {
                // We don't care
            }
        }));
    }

    public void removeAllStudyAssets() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getStudyAssetsRootPath()));
    }

    public void removeAllStudyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getStudyLogsPath()));
    }

    public void removeAllResultUploads() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getResultUploadsPath()));
    }

    public void removeAllLogs() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getLogsPath()));
    }

}
