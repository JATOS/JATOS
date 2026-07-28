package controllers.publix.workers;

import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.MTWorkerDao;
import exceptions.common.BadRequestException;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import general.common.StudyLogger;
import group.GroupAdministration;
import http.common.Http.Context;
import http.common.HttpUtils;
import json.common.DomainJsonMapper;
import models.common.*;
import models.common.workers.MTWorker;
import models.common.workers.WorkerType;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.MTStudyAuthorisation;
import utils.common.IOUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static play.test.Helpers.*;

/**
 * Unit tests for MTPublix methods (class-local logic only).
 */
public class MTPublixTest {

    private static MockedStatic<HttpUtils> httpUtilsMocked;

    private PublixUtils publixUtils;
    private MTStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private WorkerCreator workerCreator;
    private GroupAdministration groupAdministration;
    private IdCookieService idCookieService;
    private MTWorkerDao mtWorkerDao;
    private StudyLogger studyLogger;

    private MTPublix publix;

    @BeforeClass
    public static void initStatics() {
        httpUtilsMocked = mockStatic(HttpUtils.class, CALLS_REAL_METHODS);
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);
    }

    @AfterClass
    public static void closeStatics() {
        if (httpUtilsMocked != null) httpUtilsMocked.close();
    }

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(MTStudyAuthorisation.class);
        resultCreator = mock(ResultCreator.class);
        workerCreator = mock(WorkerCreator.class);
        groupAdministration = mock(GroupAdministration.class);
        idCookieService = mock(IdCookieService.class);
        mtWorkerDao = mock(MTWorkerDao.class);
        studyLogger = mock(StudyLogger.class);

        StudyAssets studyAssets = mock(StudyAssets.class);
        PublixErrorMessages errorMessages = mock(PublixErrorMessages.class);
        DomainJsonMapper domainJsonMapper = mock(DomainJsonMapper.class);
        ComponentResultDao componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed here
        IOExecutor ioExecutor = mock(IOExecutor.class);
        StudyAssetsExecutor studyAssetsExecutor = mock(StudyAssetsExecutor.class);

        publix = new MTPublix(publixUtils, studyAuthorisation, resultCreator, workerCreator,
                groupAdministration, idCookieService, errorMessages, studyAssets, domainJsonMapper,
                componentResultDao, studyResultDao, mtWorkerDao, studyLogger, ioUtils, ioExecutor,
                studyAssetsExecutor);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    private static Study newStudy(long id) {
        Study study = new Study();
        study.setId(id);
        return study;
    }

    private static Batch newBatch(long id, Study study) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setStudy(study);
        return batch;
    }

    private static StudyLink newStudyLink(Batch batch) {
        StudyLink studyLink = new StudyLink();
        studyLink.setBatch(batch);
        studyLink.setStudyCode("code-mt");
        studyLink.setWorkerType(WorkerType.MT);
        return studyLink;
    }

    private static MTWorker newWorker(long id) {
        MTWorker worker = mock(MTWorker.class);
        when(worker.getId()).thenReturn(id);
        return worker;
    }

    private static StudyResult newStudyResult(long id, String uuid, Study study, Batch batch, MTWorker worker) {
        StudyResult studyResult = new StudyResult();
        studyResult.setId(id);
        studyResult.setUuid(uuid);
        studyResult.setStudy(study);
        studyResult.setBatch(batch);
        studyResult.setWorker(worker);
        return studyResult;
    }

    private static Component newComponent(String uuid) {
        Component component = new Component();
        component.setUuid(uuid);
        return component;
    }

    // -------------------- startStudy --------------------

    @Test
    public void startStudy_existingMTWorker_createsStudyResultAndRedirects() {
        Study study = newStudy(100L);
        Batch batch = newBatch(200L, study);
        StudyLink studyLink = newStudyLink(batch);
        Http.Request request = fakeRequest("GET", "/publix/code-mt?workerId=mt-worker-1").build();
        Context.setCurrent(new Context(request));

        MTWorker worker = newWorker(300L);
        when(mtWorkerDao.findByMTWorkerId("mt-worker-1", WorkerType.MT)).thenReturn(Optional.of(worker));

        Component firstComponent = newComponent("comp-uuid-mt-1");
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(firstComponent);

        StudyResult studyResult = newStudyResult(400L, "sr-uuid-mt-1", study, batch, worker);
        when(resultCreator.createStudyResult(studyLink, worker)).thenReturn(studyResult);

        Result result = publix.startStudy(request, studyLink);

        assertEquals(SEE_OTHER, result.status());
        String location = result.header("Location").orElse("");
        assertTrue("Redirect should go to startComponent of first component",
                location.endsWith("/publix/sr-uuid-mt-1/comp-uuid-mt-1/start"));

        verify(mtWorkerDao).findByMTWorkerId("mt-worker-1", WorkerType.MT);
        verify(workerCreator, never()).createAndPersistMTWorker(anyString(), anyBoolean(), any());
        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResults();
        verify(resultCreator).createStudyResult(studyLink, worker);
        verify(publixUtils).setUrlQueryParameter(studyResult);
        verify(idCookieService).writeIdCookie(studyResult);
        verify(studyLogger).log(eq(studyLink), contains("Started study run with " + WorkerType.MT), eq(worker));
    }

    @Test
    public void startStudy_newMTWorker_createsWorkerStudyResultAndRedirects() {
        Study study = newStudy(101L);
        Batch batch = newBatch(201L, study);
        StudyLink studyLink = newStudyLink(batch);
        Http.Request request = fakeRequest("GET", "/publix/code-mt?workerId=mt-worker-2").build();
        Context.setCurrent(new Context(request));

        MTWorker worker = newWorker(301L);
        when(mtWorkerDao.findByMTWorkerId("mt-worker-2", WorkerType.MT)).thenReturn(Optional.empty());
        when(workerCreator.createAndPersistMTWorker("mt-worker-2", false, batch)).thenReturn(worker);

        Component firstComponent = newComponent("comp-uuid-mt-2");
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(firstComponent);

        StudyResult studyResult = newStudyResult(401L, "sr-uuid-mt-2", study, batch, worker);
        when(resultCreator.createStudyResult(studyLink, worker)).thenReturn(studyResult);

        Result result = publix.startStudy(request, studyLink);

        assertEquals(SEE_OTHER, result.status());
        String location = result.header("Location").orElse("");
        assertTrue(location.endsWith("/publix/sr-uuid-mt-2/comp-uuid-mt-2/start"));

        verify(mtWorkerDao).findByMTWorkerId("mt-worker-2", WorkerType.MT);
        verify(workerCreator).createAndPersistMTWorker("mt-worker-2", false, batch);
        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResults();
        verify(resultCreator).createStudyResult(studyLink, worker);
        verify(publixUtils).setUrlQueryParameter(studyResult);
        verify(idCookieService).writeIdCookie(studyResult);
    }

    @Test
    public void startStudy_sandboxRequest_createsSandboxWorker() {
        Study study = newStudy(102L);
        Batch batch = newBatch(202L, study);
        StudyLink studyLink = newStudyLink(batch);
        Http.Request request = fakeRequest("GET",
                "/publix/code-mt?workerId=mt-worker-sandbox&turkSubmitTo=https://workersandbox.mturk.com")
                .build();
        Context.setCurrent(new Context(request));

        MTWorker worker = newWorker(302L);
        when(mtWorkerDao.findByMTWorkerId("mt-worker-sandbox", WorkerType.MT_SANDBOX)).thenReturn(Optional.empty());
        when(workerCreator.createAndPersistMTWorker("mt-worker-sandbox", true, batch)).thenReturn(worker);

        Component firstComponent = newComponent("comp-uuid-mt-sandbox");
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(firstComponent);

        StudyResult studyResult = newStudyResult(402L, "sr-uuid-mt-sandbox", study, batch, worker);
        when(resultCreator.createStudyResult(studyLink, worker)).thenReturn(studyResult);

        Result result = publix.startStudy(request, studyLink);

        assertEquals(SEE_OTHER, result.status());
        verify(mtWorkerDao).findByMTWorkerId("mt-worker-sandbox", WorkerType.MT_SANDBOX);
        verify(workerCreator).createAndPersistMTWorker("mt-worker-sandbox", true, batch);
        verify(idCookieService).writeIdCookie(studyResult);
    }

    @Test(expected = BadRequestException.class)
    public void startStudy_missingWorkerId_throwsBadRequest() {
        Study study = newStudy(103L);
        Batch batch = newBatch(203L, study);
        StudyLink studyLink = newStudyLink(batch);
        Http.Request request = fakeRequest("GET", "/publix/code-mt").build();
        Context.setCurrent(new Context(request));

        publix.startStudy(request, studyLink);
    }

    @Test(expected = BadRequestException.class)
    public void startStudy_assignmentPreview_throwsBadRequest() {
        Study study = newStudy(104L);
        Batch batch = newBatch(204L, study);
        StudyLink studyLink = newStudyLink(batch);
        Http.Request request = fakeRequest("GET",
                "/publix/code-mt?workerId=mt-worker-preview&assignmentId=ASSIGNMENT_ID_NOT_AVAILABLE")
                .build();
        Context.setCurrent(new Context(request));

        publix.startStudy(request, studyLink);
    }

    // -------------------- finishStudy --------------------

    @Test
    public void finishStudy_ajaxNotDone_finishesLeavesDiscardsCookieAndReturnsConfirmationCode() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);

        Study study = newStudy(105L);
        Batch batch = newBatch(205L, study);
        MTWorker worker = newWorker(305L);
        StudyResult studyResult = newStudyResult(405L, "sr-uuid-mt-finish", study, batch, worker);
        Http.Request request = fakeRequest().build();

        when(publixUtils.finishStudyRun(true, "done", studyResult)).thenReturn("CONFIRM-123");

        Result result = publix.finishStudy(request, studyResult, true, "done");

        assertEquals(OK, result.status());
        assertEquals("CONFIRM-123", contentAsString(result));

        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishStudyRun(true, "done", studyResult);
        verify(groupAdministration).leave(studyResult);
        verify(idCookieService).discardIdCookie(studyResult.getId());
        verify(studyLogger).log(eq(study), contains("Finished study run"), eq(worker));
    }

    @Test
    public void finishStudy_ajaxAlreadyDone_returnsExistingConfirmationCode() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);

        Study study = newStudy(106L);
        Batch batch = newBatch(206L, study);
        MTWorker worker = newWorker(306L);
        StudyResult studyResult = newStudyResult(406L, "sr-uuid-mt-already-finished", study, batch, worker);
        studyResult.setStudyState(StudyResult.StudyState.FINISHED);
        studyResult.setConfirmationCode("EXISTING-CONFIRMATION");
        Http.Request request = fakeRequest().build();

        Result result = publix.finishStudy(request, studyResult, true, null);

        assertEquals(OK, result.status());
        assertEquals("EXISTING-CONFIRMATION", contentAsString(result));

        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(worker), eq(study), eq(batch));
        verify(publixUtils, never()).finishStudyRun(anyBoolean(), any(), any());
        verify(groupAdministration, never()).leave(any());
        verify(idCookieService).discardIdCookie(studyResult.getId());
        verify(studyLogger).log(eq(study), contains("Finished study run"), eq(worker));
    }
}