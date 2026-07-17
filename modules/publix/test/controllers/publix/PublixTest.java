package controllers.publix;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenReloadException;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import general.common.Common;
import general.common.StudyLogger;
import group.GroupAdministration;
import json.common.DomainJsonMapper;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.libs.Files.TemporaryFile;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.StudyAuthorisation;
import services.publix.idcookie.IdCookieService;
import testutils.publix.JPAMocker;
import utils.common.IOUtils;

import javax.persistence.EntityManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.mvc.Results.ok;
import static play.test.Helpers.*;

/**
 * Unit tests for the Publix controller base class.
 */
public class PublixTest {

    // Minimal concrete subclass for testing (Publix itself is abstract only by generic type)
    private static class TestPublix extends Publix {
        public TestPublix(PublixUtils publixUtils, StudyAuthorisation studyAuthorisation,
                          GroupAdministration groupAdministration, IdCookieService idCookieService,
                          PublixErrorMessages errorMessages, StudyAssets studyAssets, DomainJsonMapper domainJsonMapper,
                          ComponentResultDao componentResultDao, StudyResultDao studyResultDao,
                          StudyLogger studyLogger, IOUtils ioUtils, IOExecutor ioExecutor,
                          StudyAssetsExecutor studyAssetsExecutor) {
            super(publixUtils, studyAuthorisation, groupAdministration, idCookieService, errorMessages,
                    studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils, ioExecutor,
                    studyAssetsExecutor);
        }

        @Override
        public Result startStudy(Http.Request request, StudyLink studyLink) {
            return ok();
        }
    }

    private static MockedStatic<Common> commonStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = mockStatic(Common.class);
        // Ensure IOUtils static initialization (TMP_DIR) can access a valid tmp path
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    private PublixUtils publixUtils;
    private StudyAuthorisation studyAuthorisation;
    private GroupAdministration groupAdministration;
    private IdCookieService idCookieService;
    private PublixErrorMessages errorMessages;
    private StudyAssets studyAssets;
    private DomainJsonMapper domainJsonMapper;
    private ComponentResultDao componentResultDao;
    private StudyResultDao studyResultDao;
    private StudyLogger studyLogger;
    private IOUtils ioUtils;
    private IOExecutor ioExecutor;
    private StudyAssetsExecutor studyAssetsExecutor;

    private TestPublix publix;

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(StudyAuthorisation.class);
        groupAdministration = mock(GroupAdministration.class);
        idCookieService = mock(IdCookieService.class);
        errorMessages = mock(PublixErrorMessages.class);
        studyAssets = mock(StudyAssets.class);
        domainJsonMapper = mock(DomainJsonMapper.class);
        componentResultDao = mock(ComponentResultDao.class);
        studyResultDao = mock(StudyResultDao.class);
        studyLogger = mock(StudyLogger.class);
        ioUtils = null; // Not needed for tested paths and heavy to initialize
        ioExecutor = mock(IOExecutor.class);
        studyAssetsExecutor = mock(StudyAssetsExecutor.class);

        publix = new TestPublix(publixUtils, studyAuthorisation, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        EntityManager entityManager = Mockito.mock(EntityManager.class);
        JPAMocker.mockDaoTransactions(entityManager, componentResultDao, studyResultDao);
    }

    private static StudyResult newStudyResult(Study study, Batch batch, Worker worker) {
        StudyResult sr = new StudyResult();
        sr.setStudy(study);
        sr.setBatch(batch);
        sr.setWorker(worker);
        sr.setStudyState(StudyState.STARTED);
        sr.setId(10L);
        sr.setUuid("uuid-10");
        return sr;
    }

    private static Component newComponent(long id) {
        Component c = new Component();
        c.setId(id);
        c.setHtmlFilePath("index.html");
        return c;
    }

    private static ComponentResult newComponentResult(long id) {
        ComponentResult cr = new ComponentResult();
        cr.setId(id);
        cr.setComponentState(ComponentState.STARTED);
        cr.setDataSize(0);
        return cr;
    }

    private static Http.Request mockTextRequest(String body) {
        Http.Request request = mock(Http.Request.class, RETURNS_DEEP_STUBS);
        when(request.body().asText()).thenReturn(body);
        // Also mock session access to avoid NPEs when authorisation checks occur
        when(request.session()).thenReturn(mock(Http.Session.class));
        return request;
    }

    @Test(expected = ForbiddenReloadException.class)
    public void startComponent_redirectsOnForbiddenReloadOrNonLinear() {
        // Arrange
        Study study = new Study();
        study.setUuid("s-uuid");
        study.setDirName("studyDir");
        Component component = newComponent(5L);
        Worker worker = new GeneralSingleWorker();
        Batch batch = new Batch();
        StudyResult sr = newStudyResult(study, batch, worker);

        // publixUtils.startComponent throws -> should redirect to finishStudy
        when(publixUtils.startComponentRun(any(), any(), any())).thenAnswer(inv -> {
            throw new ForbiddenReloadException(study.getUuid(), "nope");
        });

        // Act
        // Throws ForbiddenReloadException
        publix.startComponent(mockTextRequest(""), sr, component, "msg");
    }

    @Test
    public void getInitData_updatesStatesAndReturnsOk() {
        Study study = new Study();
        study.setDirName("dir");
        Component component = newComponent(7L);
        Worker worker = new MTWorker();
        Batch batch = new Batch();
        batch.setId(3L);
        StudyResult sr = newStudyResult(study, batch, worker);
        sr.setStudyState(StudyState.STARTED);

        ComponentResult cr = newComponentResult(20L);
        when(publixUtils.retrieveStartedComponentResult(component, sr)).thenReturn(cr);
        when(domainJsonMapper.initData(batch, sr, study, component)).thenReturn(Json.newObject());

        Result result = publix.getInitData(mockTextRequest(""), sr, component);

        assertEquals(OK, result.status());
        assertEquals(ComponentState.DATA_RETRIEVED, cr.getComponentState());
        // StudyResult should be DATA_RETRIEVED if it was not PRE
        assertEquals(StudyState.DATA_RETRIEVED, sr.getStudyState());
        verify(componentResultDao).merge(cr);
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void setStudySessionData_updatesDaoWithTextBody() {
        Study study = new Study();
        StudyResult sr = newStudyResult(study, new Batch(), new MTWorker());
        String sessionData = "{\"foo\":1}";

        Result result = publix.setStudySessionData(mockTextRequest(sessionData), sr);

        assertEquals(OK, result.status());
        verify(studyResultDao).updateStudySessionData(sr.getId(), sessionData);
    }

    @Test
    public void heartbeat_setsLastSeenAndUpdates() {
        StudyResult sr = newStudyResult(new Study(), new Batch(), new GeneralSingleWorker());
        // Set lastSeen to a value before the heartbeat call
        sr.setLastSeenDate(new Timestamp(new Date().getTime()));
        Timestamp before = sr.getLastSeenDate();

        Result result = publix.heartbeat(mockTextRequest(""), sr);

        assertEquals(OK, result.status());
        // lastSeen should be set to a recent value
        assertNotNull(sr.getLastSeenDate());
        Timestamp after = sr.getLastSeenDate();
        assertTrue(after.getTime() - before.getTime() > 0);
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void submitOrAppendResultData_forbiddenIfNoCurrentComponentResult() {
        Study study = new Study();
        Component component = newComponent(1L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());

        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.empty());

        Result result = publix.submitOrAppendResultData(mockTextRequest("{}"), sr, component, false);

        assertEquals(FORBIDDEN, result.status());
        verify(componentResultDao, never()).replaceData(anyLong(), anyString());
    }

    @Test
    public void submitOrAppendResultData_dataSizeTooLarge() {
        Study study = new Study();
        Component component = newComponent(2L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        ComponentResult cr = newComponentResult(11L);
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(cr));

        // Set max size of result data to 1 byte
        //noinspection ResultOfMethodCallIgnored
        commonStatic.when(Common::getResultDataMaxSize).thenReturn(1L);

        Result r = publix.submitOrAppendResultData(mockTextRequest("foo"), sr, component, false);
        assertEquals(REQUEST_ENTITY_TOO_LARGE, r.status());
        verify(componentResultDao, never()).replaceData(anyLong(), anyString());
    }

    @Test
    public void submitOrAppendResultData_appendVsReplace_basedOnFlag() {
        Study study = new Study();
        Component component = newComponent(2L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        ComponentResult cr = newComponentResult(11L);
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(cr));

        // Set max size of result data to 5000 bytes
        //noinspection ResultOfMethodCallIgnored
        commonStatic.when(Common::getResultDataMaxSize).thenReturn(5000L);

        Result r1 = publix.submitOrAppendResultData(mockTextRequest("foo"), sr, component, false);
        assertEquals(OK, r1.status());
        verify(componentResultDao).replaceData(cr.getId(), "foo");

        Result r2 = publix.submitOrAppendResultData(mockTextRequest("bar"), sr, component, true);
        assertEquals(OK, r2.status());
        verify(componentResultDao).appendData(cr.getId(), "bar");
    }

    @Test
    public void uploadResultFile_forbiddenIfUploadsDisabled() {
        Study study = new Study();
        Component component = newComponent(3L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());

        // Disable result uploads
        //noinspection ResultOfMethodCallIgnored
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(false);

        Result result = publix.uploadResultFile(mockMultipartRequestWithoutFile(), sr, component, "foo.txt");

        assertEquals(FORBIDDEN, result.status());
    }

    @Test
    public void uploadResultFile_missingFile_returnsBadRequest() {
        // Enable uploads
        //noinspection ResultOfMethodCallIgnored
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(true);
        Study study = new Study();
        Component component = newComponent(30L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        // Need current ComponentResult
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(newComponentResult(100L)));

        Result result = publix.uploadResultFile(mockMultipartRequestWithoutFile(), sr, component, "any.txt");

        assertEquals(BAD_REQUEST, result.status());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void uploadResultFile_fileTooLarge_usesCommonMaxSize_returnsBadRequest() {
        // Enable uploads and set limits
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(true);
        commonStatic.when(Common::getResultUploadsMaxFileSize).thenReturn(100L);
        commonStatic.when(Common::getResultUploadsLimitPerStudyRun).thenReturn(Long.MAX_VALUE);

        // Mock ioUtils and re-create publix with it
        ioUtils = mock(IOUtils.class);
        publix = new TestPublix(publixUtils, studyAuthorisation, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        Study study = new Study();
        Component component = newComponent(31L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(newComponentResult(101L)));

        // Create request with file size larger than allowed
        TemporaryFile tmp = mock(TemporaryFile.class);
        Http.Request req = mockMultipartRequestWithFile(200L, tmp);

        // For filename check pass and dir size ok
        try (MockedStatic<IOUtils> ioStatic = mockStatic(IOUtils.class)) {
            ioStatic.when(() -> IOUtils.checkFilename("big.bin")).thenReturn(true);
            when(ioUtils.getResultUploadDirSize(sr.getId())).thenReturn(0L);

            Result result = publix.uploadResultFile(req, sr, component, "big.bin");
            assertEquals(REQUEST_ENTITY_TOO_LARGE, result.status());
            verify(tmp, never()).moveTo(any(Path.class), anyBoolean());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void uploadResultFile_limitPerStudyRunExceeded_returnsBadRequest() {
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(true);
        commonStatic.when(Common::getResultUploadsMaxFileSize).thenReturn(Long.MAX_VALUE);
        commonStatic.when(Common::getResultUploadsLimitPerStudyRun).thenReturn(10L);

        ioUtils = mock(IOUtils.class);
        publix = new TestPublix(publixUtils, studyAuthorisation, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        Study study = new Study();
        Component component = newComponent(32L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(newComponentResult(102L)));

        TemporaryFile tmp = mock(TemporaryFile.class);
        Http.Request req = mockMultipartRequestWithFile(5L, tmp);

        try (MockedStatic<IOUtils> ioStatic = mockStatic(IOUtils.class)) {
            ioStatic.when(() -> IOUtils.checkFilename("ok.txt")).thenReturn(true);
            when(ioUtils.getResultUploadDirSize(sr.getId())).thenReturn(100L); // exceed limit

            Result result = publix.uploadResultFile(req, sr, component, "ok.txt");
            assertEquals(REQUEST_ENTITY_TOO_LARGE, result.status());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void uploadResultFile_badFilename_returnsBadRequest() {
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(true);
        commonStatic.when(Common::getResultUploadsMaxFileSize).thenReturn(Long.MAX_VALUE);
        commonStatic.when(Common::getResultUploadsLimitPerStudyRun).thenReturn(Long.MAX_VALUE);

        ioUtils = mock(IOUtils.class);
        publix = new TestPublix(publixUtils, studyAuthorisation, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        Study study = new Study();
        Component component = newComponent(33L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(newComponentResult(103L)));

        TemporaryFile tmp = mock(TemporaryFile.class);
        Http.Request req = mockMultipartRequestWithFile(1L, tmp);

        try (MockedStatic<IOUtils> ioStatic = mockStatic(IOUtils.class)) {
            ioStatic.when(() -> IOUtils.checkFilename("bad?.txt")).thenReturn(false);
            when(ioUtils.getResultUploadDirSize(sr.getId())).thenReturn(0L);

            Result result = publix.uploadResultFile(req, sr, component, "bad?.txt");
            assertEquals(BAD_REQUEST, result.status());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void uploadResultFile_success_movesFileAndLogs_ok() throws Exception {
        commonStatic.when(Common::isResultUploadsEnabled).thenReturn(true);
        commonStatic.when(Common::getResultUploadsMaxFileSize).thenReturn(Long.MAX_VALUE);
        commonStatic.when(Common::getResultUploadsLimitPerStudyRun).thenReturn(Long.MAX_VALUE);

        ioUtils = mock(IOUtils.class);
        publix = new TestPublix(publixUtils, studyAuthorisation, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        Study study = new Study();
        Component component = newComponent(34L);
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());
        ComponentResult cr = newComponentResult(104L);
        when(publixUtils.retrieveCurrentComponentResult(sr)).thenReturn(Optional.of(cr));

        TemporaryFile tmp = mock(TemporaryFile.class);
        Http.Request req = mockMultipartRequestWithFile(1L, tmp);

        Path dstFile = Path.of("/tmp/uploaded-ok.bin");
        when(ioUtils.getResultUploadDirSize(sr.getId())).thenReturn(0L);
        when(ioUtils.getResultUploadFileSecurely(sr.getId(), cr.getId(), "good.bin")).thenReturn(dstFile);

        try (MockedStatic<IOUtils> ioStatic = mockStatic(IOUtils.class)) {
            ioStatic.when(() -> IOUtils.checkFilename("good.bin")).thenReturn(true);

            Result result = publix.uploadResultFile(req, sr, component, "good.bin");
            assertEquals(OK, result.status());
            verify(tmp).moveTo(any(Path.class), eq(true));
            verify(studyLogger).logResultUploading(any(Path.class), eq(cr));
        }
    }

    @Test
    public void downloadResultFile_notFoundIfNoFilePresent() {
        Study study = new Study();
        Worker worker = new GeneralSingleWorker();
        Batch batch = new Batch();
        StudyResult sr = newStudyResult(study, batch, worker);
        when(publixUtils.retrieveLastUploadedResultFile(eq(sr), isNull(), eq("bar.bin")))
                .thenReturn(Optional.empty());

        Result result = publix.downloadResultFile(mockTextRequest(""), sr, "bar.bin", null);

        assertEquals(NOT_FOUND, result.status());
        // Authorisation is checked irrespective of file presence
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(worker), eq(study), eq(batch));
    }

    @Test(expected = NumberFormatException.class)
    public void downloadResultFile_badComponentId_throwsNumberFormat() {
        Study study = new Study();
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());

        // componentId must be numeric; a non-numeric should cause NumberFormatException
        publix.downloadResultFile(mockTextRequest(""), sr, "x.bin", "abc");
    }

    @Test
    public void downloadResultFile_notFound_withComponentId_whenNoFile() {
        Study study = new Study();
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());

        Component comp = newComponent(77L);
        when(publixUtils.retrieveComponent(study, 77L)).thenReturn(comp);
        when(publixUtils.retrieveLastUploadedResultFile(eq(sr), eq(comp), eq("nofile.txt")))
                .thenReturn(Optional.empty());

        Result result = publix.downloadResultFile(mockTextRequest(""), sr, "nofile.txt", "77");

        assertEquals(NOT_FOUND, result.status());
    }

    @Test
    public void downloadResultFile_success() throws Exception {
        Study study = new Study();
        StudyResult sr = newStudyResult(study, new Batch(), new GeneralSingleWorker());

        Component comp = newComponent(77L);
        Path file = Files.createTempFile("uploaded-ok", ".bin");
        file.toFile().deleteOnExit();
        when(publixUtils.retrieveComponent(study, 77L)).thenReturn(comp);
        when(publixUtils.retrieveLastUploadedResultFile(eq(sr), eq(comp), eq("file.txt")))
                .thenReturn(Optional.of(file));

        Result result = publix.downloadResultFile(mockTextRequest(""), sr, "file.txt", "77");

        assertEquals(OK, result.status());
    }

    @Test
    public void log_sanitizesAndReturnsOk() {
        Study study = new Study();
        study.setId(9L);
        Batch batch = new Batch();
        batch.setId(8L);
        Component component = newComponent(7L);
        Worker worker = new GeneralSingleWorker();
        worker.setId(6L);
        StudyResult sr = newStudyResult(study, batch, worker);

        Result result = publix.log(mockTextRequest("Hello\nWorld\t  !!  "), sr, component);

        assertEquals(OK, result.status());
        // We mostly ensure it doesn't throw and calls authorisation.
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(worker), eq(study), eq(batch));
    }

    // Helpers to craft HTTP requests
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Http.Request mockMultipartRequestWithoutFile() {
        Http.Request request = mock(Http.Request.class, RETURNS_DEEP_STUBS);
        Http.MultipartFormData<?> m = mock(Http.MultipartFormData.class);
        when(request.body().asMultipartFormData()).thenReturn((Http.MultipartFormData) m);
        when(m.getFile("file")).thenReturn(null);
        when(request.session()).thenReturn(mock(Http.Session.class));
        return request;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Http.Request mockMultipartRequestWithFile(long fileSize, TemporaryFile tmp) {
        Http.Request request = mock(Http.Request.class, RETURNS_DEEP_STUBS);
        Http.MultipartFormData<TemporaryFile> m = mock(Http.MultipartFormData.class);
        Http.MultipartFormData.FilePart<TemporaryFile> fp = mock(Http.MultipartFormData.FilePart.class);
        when(fp.getFileSize()).thenReturn(fileSize);
        when(fp.getRef()).thenReturn(tmp);
        when(m.getFile("file")).thenReturn(fp);
        when(request.body().asMultipartFormData()).thenReturn((Http.MultipartFormData) m);
        when(request.session()).thenReturn(mock(Http.Session.class));
        return request;
    }
}
