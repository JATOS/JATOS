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
import general.common.Common;
import models.common.Study;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static play.test.Helpers.*;

/**
 * @author Kristian Lange
 */
public class JatosTest {

    @Inject
    private Application application;

    @Inject
    private JPAApi jpaApi;

    public User admin;

    public String apiToken;

    @Before
    public void startApp() {
        application = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Guice.createInjector(builder.applicationModule()).injectMembers(this);

        Helpers.start(application);

        admin = getAdmin();
        apiToken = getApiToken(admin);
    }

    @After
    public void stopApp() throws Exception {
        removeAllStudies(); // Remove studies because H2 doesn't get cleared between tests

        Helpers.stop(application);

        removeAllStudyAssets();
        removeAllResultUploads();
        removeAllStudyLogs();
        removeAllLogs();
    }

    /**
     * Remove all studies in the database and delete their study assets and result files
     */
    public void removeAllStudies() {
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        StudyService studyService = application.injector().instanceOf(StudyService.class);
        jpaApi.withTransaction(() -> studyDao.findAll().forEach(study -> {
            try {
                studyService.removeStudyInclAssets(study, getAdmin());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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

    @Test
    public void testImportExampleStudy() {
        try {
            Result result = importExampleStudy();
            assertEquals(200, result.status());
            JsonNode content = Json.parse(contentAsString(result));
            assertEquals("Potato Compass", content.get("uploadedStudyTitle").textValue());
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    public Result importExampleStudy() {
        TemporaryFileCreator temporaryFileCreator = application.injector().instanceOf(TemporaryFileCreator.class);
        Materializer materializer = application.injector().instanceOf(Materializer.class);
        Path studyPath = Paths.get(Common.getBasepath(), "/test/resources/potato_compass.jzip");
        Source<ByteString, CompletionStage<IOResult>> source = FileIO.fromPath(studyPath);
        Http.MultipartFormData.FilePart<Source<ByteString, ?>> part = new Http.MultipartFormData.FilePart<>("study", "filename", "text/plain", source);
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(POST)
                .header("Authorization", "Bearer " + apiToken)
                .bodyMultipart(Collections.singletonList(part), temporaryFileCreator, materializer)
                .uri("/jatos/api/v1/study?keepProperties=false&keepAssets=false&keepCurrentAssetsName=false&renameAssets=false");

        return route(application, request);
    }

    public Study getExampleStudy() {
        Result result = importExampleStudy();
        JsonNode content = Json.parse(contentAsString(result));
        Long studyId = content.get("id").asLong();
        return getStudy(studyId);
    }

    public User getAdmin() {
        UserDao userDao = application.injector().instanceOf(UserDao.class);
        return jpaApi.withTransaction(() -> userDao.findByUsername(UserService.ADMIN_USERNAME));
    }

    public String getApiToken(User user) {
        ApiTokenService apiTokenService = application.injector().instanceOf(ApiTokenService.class);
        return jpaApi.withTransaction(() -> apiTokenService.create(user, "test-token", 0));
    }

    public Study getStudy(Long id) {
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        return jpaApi.withTransaction(() -> {
            Study study = studyDao.findById(id);
            utils.common.Helpers.initializeAndUnproxy(study.getDefaultBatch());
            utils.common.Helpers.initializeAndUnproxy(study.getComponentList());
            return study;
        });
    }

}
