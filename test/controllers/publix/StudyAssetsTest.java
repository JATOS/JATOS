package controllers.publix;

import org.apache.pekko.stream.Materializer;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import general.common.Common;
import models.common.Component;
import models.common.Study;
import models.common.StudyLink;
import models.common.StudyResult;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for StudyAssets.
 */
public class StudyAssetsTest extends JatosTest {

    @Test
    public void enhanceQueryStringInEndRedirectUrl_replacesAndEncodes() {
        StudyAssets studyAssets = application.injector().instanceOf(StudyAssets.class);
        String urlQueryParameters = "{\"batchId\":\"1\",\"SONA_ID\":\"123 abc\"}";
        String endRedirectUrl = "https://example.org/end?foo=100&survey_id=[SONA_ID]&bar=[unknown]";

        String result = studyAssets.enhanceQueryStringInEndRedirectUrl(urlQueryParameters, endRedirectUrl);

        // SONA_ID should be URL-encoded (space -> '+'), unknown becomes 'undefined'
        assertThat(result).isEqualTo("https://example.org/end?foo=100&survey_id=123+abc&bar=undefined");
    }

    @Test
    public void viaStudyPath_withJatosJs_servesBundledJatosJs() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/publix/anyStudyResultUuid/anyComponentUuid/jatos.js");

        Result result = route(application, request);
        Materializer materializer = application.injector().instanceOf(Materializer.class);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(contentAsString(result, materializer)).contains("jatos");
    }

    @Test
    public void viaStudyPath_withJatosPublixPath_servesBundledPublixAsset() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/publix/anyStudyResultUuid/anyComponentUuid/jatos-publix/javascripts/jatos.js");

        Result result = route(application, request);
        Materializer materializer = application.injector().instanceOf(Materializer.class);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(contentAsString(result, materializer)).contains("jatos");
    }

    @Test
    public void viaStudyPath_withStudyAssetPath_servesFileFromImportedStudyAssets() throws Exception {
        StudyRunInfo studyRunInfo = importExampleStudyAndCreateStudyResult();
        Path assetPath = Paths.get(
                Common.getStudyAssetsRootPath(),
                studyRunInfo.studyDirName,
                "study-assets-test.txt");
        Files.writeString(assetPath, "hello from study assets");

        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/publix/"
                        + studyRunInfo.studyResultUuid
                        + "/"
                        + studyRunInfo.componentUuid
                        + "/study-assets-test.txt")
                .cookie(studyAssetsCookie(studyRunInfo));

        Result result = route(application, request);
        Materializer materializer = application.injector().instanceOf(Materializer.class);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(contentAsString(result, materializer)).isEqualTo("hello from study assets");
    }

    @Test
    public void viaStudyPath_withStudyAssetPath_forbiddenWithoutMatchingStudyAssetsCookie() throws Exception {
        StudyRunInfo studyRunInfo = importExampleStudyAndCreateStudyResult();
        Path assetPath = Paths.get(
                Common.getStudyAssetsRootPath(),
                studyRunInfo.studyDirName,
                "study-assets-test.txt");
        Files.writeString(assetPath, "hello from study assets");

        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/publix/"
                        + studyRunInfo.studyResultUuid
                        + "/"
                        + studyRunInfo.componentUuid
                        + "/study-assets-test.txt");

        Result result = route(application, request);

        assertThat(result.status()).isEqualTo(FORBIDDEN);
    }

    private StudyRunInfo importExampleStudyAndCreateStudyResult() {
        Long studyId = importExampleStudy();
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        StudyLinkDao studyLinkDao = application.injector().instanceOf(StudyLinkDao.class);

        return jpaApi.withTransaction(em -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().orElseThrow();
            StudyLink studyLink = studyLinkDao.persist(new StudyLink(study.getDefaultBatch(), admin.getWorker().getWorkerType()));
            StudyResult studyResult = new StudyResult(studyLink, admin.getWorker());
            studyResult.setUuid(UUID.randomUUID().toString());
            em.persist(studyResult);
            em.flush();

            return new StudyRunInfo(
                    study.getDirName(),
                    studyResult.getUuid(),
                    component.getUuid());
        });
    }

    private Http.Cookie studyAssetsCookie(StudyRunInfo studyRunInfo) {
        return Http.Cookie.builder(
                        "JATOS_ID_1",
                        "workerId=" + admin.getWorker().getId()
                                + "&workerType=" + admin.getWorker().getWorkerType().value()
                                + "&batchId=1"
                                + "&studyId=1"
                                + "&studyResultId=1"
                                + "&studyResultUuid=" + studyRunInfo.studyResultUuid
                                + "&studyAssets=" + studyRunInfo.studyDirName
                                + "&urlBasePath=/"
                                + "&creationTime=1")
                .build();
    }

    private record StudyRunInfo(String studyDirName, String studyResultUuid, String componentUuid) {

    }

}