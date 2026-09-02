package controllers.publix.workers;

import controllers.publix.StudyAssets;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.common.ForbiddenException;
import exceptions.publix.ForbiddenReloadException;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import general.common.Common;
import general.common.StudyLogger;
import group.GroupAdministration;
import http.common.Http.Context;
import http.common.HttpUtils;
import json.common.DomainJsonMapper;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
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
import services.publix.idcookie.IdCookieService;
import services.publix.workers.JatosStudyAuthorisation;
import utils.common.IOUtils;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.test.Helpers.*;

/**
 * Unit tests for JatosPublix methods (class-local logic only).
 */
public class JatosPublixTest {

    private static MockedStatic<Common> commonMocked;
    private static MockedStatic<HttpUtils> httpUtilsMocked;

    private PublixUtils publixUtils;
    private JatosStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private GroupAdministration groupAdministration;
    private IdCookieService idCookieService;
    private StudyAssets studyAssets;
    private StudyLogger studyLogger;
    private ComponentResultDao componentResultDao;

    private JatosPublix publix;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonMocked = mockStatic(Common.class);
        commonMocked.when(Common::getTmpPath).thenReturn(tmp);
        commonMocked.when(Common::getJatosUrlBasePath).thenReturn("/");
        httpUtilsMocked = mockStatic(HttpUtils.class);
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);
    }

    @AfterClass
    public static void closeStatics() {
        if (httpUtilsMocked != null) httpUtilsMocked.close();
        if (commonMocked != null) commonMocked.close();
    }

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(JatosStudyAuthorisation.class);
        resultCreator = mock(ResultCreator.class);
        groupAdministration = mock(GroupAdministration.class);
        idCookieService = mock(IdCookieService.class);
        studyAssets = mock(StudyAssets.class);
        studyLogger = mock(StudyLogger.class);
        PublixErrorMessages errorMessages = mock(PublixErrorMessages.class);
        DomainJsonMapper domainJsonMapper = mock(DomainJsonMapper.class);
        componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed here
        IOExecutor ioExecutor = mock(IOExecutor.class);
        StudyAssetsExecutor studyAssetsExecutor = mock(StudyAssetsExecutor.class);

        publix = new JatosPublix(publixUtils, studyAuthorisation, resultCreator, groupAdministration, idCookieService,
                errorMessages, studyAssets, domainJsonMapper, componentResultDao, studyResultDao, studyLogger, ioUtils,
                ioExecutor, studyAssetsExecutor);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @SuppressWarnings("SameParameterValue")
    private static Study newStudy(long id, String uuid, String dirName) {
        Study s = new Study();
        s.setId(id);
        s.setUuid(uuid);
        s.setDirName(dirName);
        return s;
    }

    @SuppressWarnings("SameParameterValue")
    private static Batch newBatch(long id, Study study) {
        Batch b = new Batch();
        b.setId(id);
        b.setStudy(study);
        return b;
    }

    private static StudyLink newStudyLink(Batch batch) {
        StudyLink sl = new StudyLink();
        sl.setBatch(batch);
        sl.setStudyCode("code-1");
        sl.setWorkerType(WorkerType.JATOS);
        return sl;
    }

    private static Component newComponent(long id, String uuid, String html) {
        Component c = new Component();
        c.setId(id);
        c.setUuid(uuid);
        c.setHtmlFilePath(html);
        return c;
    }

    @SuppressWarnings("SameParameterValue")
    private static StudyResult newStudyResult(long id, String uuid, Study study, Batch batch, JatosWorker worker) {
        StudyResult sr = new StudyResult();
        sr.setId(id);
        sr.setUuid(uuid);
        sr.setStudy(study);
        sr.setBatch(batch);
        sr.setWorker(worker);
        sr.setStudyState(StudyState.PRE);
        return sr;
    }

    private static ComponentResult newComponentResult(long id, Component component, StudyResult sr) {
        ComponentResult cr = new ComponentResult();
        cr.setId(id);
        cr.setComponent(component);
        cr.setStudyResult(sr);
        cr.setComponentState(ComponentState.STARTED);
        return cr;
    }

    // -------------------- startStudy --------------------

    @Test
    public void startStudy_runStudy_redirectsToFirstComponent() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        jw.setId(5L);
        User user = new User();
        user.setWorker(jw);
        Http.Request request = fakeRequest().build();

        Component first = new Component();
        first.setUuid("comp-uuid-1");

        when(publixUtils.retrieveSignedinUser()).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession()).thenReturn(JatosRun.RUN_STUDY);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(first);

        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        when(resultCreator.createStudyResult(sl, jw)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/comp-uuid-1/start"));
        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(eq(jw), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyRun();
        verify(publixUtils).setUrlQueryParameter(sr);
        verify(idCookieService).writeIdCookie(sr, JatosRun.RUN_STUDY);
    }

    @Test
    public void startStudy_runComponentStart_usesSessionUuid() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        User user = new User();
        user.setWorker(jw);
        Http.Request request = fakeRequest().build();

        Context.current().response().putSession("run_component_uuid", "abc-123");

        when(publixUtils.retrieveSignedinUser()).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession()).thenReturn(JatosRun.RUN_COMPONENT_START);

        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        when(resultCreator.createStudyResult(sl, jw)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/abc-123/start"));
        verify(idCookieService).writeIdCookie(sr, JatosRun.RUN_COMPONENT_START);
    }

    @Test(expected = ForbiddenException.class)
    public void startStudy_runComponentFinished_forbidden() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        User user = new User();
        user.setWorker(jw);
        Http.Request request = fakeRequest().build();

        when(publixUtils.retrieveSignedinUser()).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession()).thenReturn(JatosRun.RUN_COMPONENT_FINISHED);

        publix.startStudy(request, sl);
    }

    // -------------------- startComponent --------------------

    @Test
    public void startComponent_runStudy_success() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        jw.setId(7L);
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");
        ComponentResult cr = newComponentResult(20L, comp, sr);

        when(idCookieService.getJatosRun(sr.getId())).thenReturn(JatosRun.RUN_STUDY);

        when(studyAssets.retrieveComponentHtmlFile("dir", "index.html")).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponentRun(comp, sr, null)).thenReturn(cr);

        Result res = publix.startComponent(fakeRequest().build(), sr, comp, null);

        assertEquals(OK, res.status());
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(jw), eq(study), eq(batch));
        verify(publixUtils).checkComponentBelongsToStudy(study, comp);
        verify(idCookieService).writeIdCookie(sr, cr, JatosRun.RUN_STUDY);
    }

    @Test
    public void startComponent_runComponentStart_transitionsToFinished() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");
        ComponentResult cr = newComponentResult(20L, comp, sr);

        when(idCookieService.getJatosRun(sr.getId())).thenReturn(JatosRun.RUN_COMPONENT_START);

        when(studyAssets.retrieveComponentHtmlFile("dir", "index.html")).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponentRun(comp, sr, null)).thenReturn(cr);

        Result res = publix.startComponent(fakeRequest().build(), sr, comp, null);

        assertEquals(OK, res.status());
        verify(idCookieService).writeIdCookie(sr, cr, JatosRun.RUN_COMPONENT_FINISHED);
    }

    @Test
    public void startComponent_runComponentFinished_nextDifferent_redirectsFinishStudy() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult srReal = newStudyResult(10L, "sr-uuid", study, batch, jw);
        StudyResult sr = spy(srReal);
        Component first = newComponent(3L, "first", "index.html");
        Component second = newComponent(4L, "second", "second.html");
        ComponentResult lastCr = newComponentResult(20L, first, srReal);
        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(java.util.Optional.of(lastCr));

        when(idCookieService.getJatosRun(sr.getId())).thenReturn(JatosRun.RUN_COMPONENT_FINISHED);

        when(studyAssets.retrieveComponentHtmlFile(anyString(), anyString())).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponentRun(second, sr, null)).thenReturn(newComponentResult(21L, second, sr));

        Result res = publix.startComponent(fakeRequest().build(), sr, second, null);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/end"));
    }

    @Test(expected = ForbiddenReloadException.class)
    public void startComponent_exception_finishesStudyUnsuccessful() {
        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");

        when(idCookieService.getJatosRun(sr.getId())).thenReturn(JatosRun.RUN_STUDY);

        when(publixUtils.startComponentRun(comp, sr, null)).thenThrow(new ForbiddenReloadException("s-uuid", "reload"));

        // ForbiddenReloadException is not caught
        publix.startComponent(fakeRequest().build(), sr, comp, null);
    }

    // -------------------- abortStudy --------------------

    @Test
    public void abortStudy_nonAjax_redirectsToJatosUrl() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(true);

        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.abortStudy(fakeRequest().build(), sr, "bye");

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.contains("/jatos/1"));
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(jw), eq(study), eq(batch));
        verify(publixUtils).abortStudyRun(eq(sr.getId()), any(), any());
        verify(idCookieService).discardIdCookie(sr.getId());
    }

    @Test
    public void abortStudy_ajax_ok() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);

        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.abortStudy(fakeRequest().build(), sr, null);
        assertEquals(OK, res.status());
    }

    // -------------------- finishStudy --------------------

    @Test
    public void finishStudy_nonAjax_redirectsToJatosUrl() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(true);

        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.finishStudy(fakeRequest().build(), sr, true, null);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.contains("/jatos/1"));
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(eq(jw), eq(study), eq(batch));
        verify(publixUtils).finishStudyRun(eq(sr.getId()), anyBoolean(), any(), any());
        verify(idCookieService).discardIdCookie(sr.getId());
    }

    @Test
    public void finishStudy_ajax_ok() {
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);

        Study study = newStudy(1L, "s-uuid", "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.finishStudy(fakeRequest().build(), sr, false, "err");
        assertEquals(OK, res.status());
    }
}
