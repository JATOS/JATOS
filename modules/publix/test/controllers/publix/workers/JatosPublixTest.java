package controllers.publix.workers;

import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import general.common.Common;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.JatosStudyAuthorisation;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

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

    private static MockedStatic<Common> commonStatic;
    private static MockedStatic<Helpers> helpersStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(Common::getJatosUrlBasePath).thenReturn("/");
        helpersStatic = mockStatic(Helpers.class);
        helpersStatic.when(Helpers::isAjax).thenReturn(false);
    }

    @AfterClass
    public static void closeStatics() {
        if (helpersStatic != null) helpersStatic.close();
        if (commonStatic != null) commonStatic.close();
    }

    private PublixUtils publixUtils;
    private JatosStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private GroupAdministration groupAdministration;
    private IdCookieService idCookieService;
    private StudyAssets studyAssets;
    private StudyLogger studyLogger;

    private JatosPublix publix;

    private final JPAApi jpa = mock(JPAApi.class);

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
        JsonUtils jsonUtils = mock(JsonUtils.class);
        ComponentResultDao componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed here

        publix = new JatosPublix(jpa, publixUtils, studyAuthorisation, resultCreator, groupAdministration, idCookieService,
                errorMessages, studyAssets, jsonUtils, componentResultDao, studyResultDao, studyLogger, ioUtils);
    }

    private static Study newStudy(long id, String dirName) {
        Study s = new Study();
        s.setId(id);
        s.setDirName(dirName);
        return s;
    }

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
        sl.setWorkerType(JatosWorker.WORKER_TYPE);
        return sl;
    }

    private static Component newComponent(long id, String uuid, String html) {
        Component c = new Component();
        c.setId(id);
        c.setUuid(uuid);
        c.setHtmlFilePath(html);
        return c;
    }

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
    public void startStudy_runStudy_redirectsToFirstComponent() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        jw.setId(5L);
        User user = new User();
        user.setWorker(jw);

        Component first = new Component();
        first.setUuid("comp-uuid-1");

        Http.Request request = fakeRequest().session("username", "alice").build();

        when(publixUtils.retrieveSignedinUser(any())).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession(any())).thenReturn(JatosPublix.JatosRun.RUN_STUDY);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(first);

        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        when(resultCreator.createStudyResult(sl, jw)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/comp-uuid-1/start"));
        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(any(), eq(jw), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResult();
        verify(publixUtils).setUrlQueryParameter(request, sr);
        verify(idCookieService).writeIdCookie(sr, JatosPublix.JatosRun.RUN_STUDY);
    }

    @Test
    public void startStudy_runComponentStart_usesSessionUuid() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        User user = new User();
        user.setWorker(jw);

        Http.Request request = fakeRequest().session("run_component_uuid", "abc-123").build();

        when(publixUtils.retrieveSignedinUser(any())).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession(any())).thenReturn(JatosPublix.JatosRun.RUN_COMPONENT_START);

        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        when(resultCreator.createStudyResult(sl, jw)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/abc-123/start"));
        verify(idCookieService).writeIdCookie(sr, JatosPublix.JatosRun.RUN_COMPONENT_START);
    }

    @Test(expected = ForbiddenPublixException.class)
    public void startStudy_runComponentFinished_forbidden() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        StudyLink sl = newStudyLink(batch);
        JatosWorker jw = new JatosWorker();
        User user = new User();
        user.setWorker(jw);
        Http.Request request = fakeRequest().build();

        when(publixUtils.retrieveSignedinUser(any())).thenReturn(user);
        when(publixUtils.fetchJatosRunFromSession(any())).thenReturn(JatosPublix.JatosRun.RUN_COMPONENT_FINISHED);

        publix.startStudy(request, sl);
    }

    // -------------------- startComponent --------------------

    @Test
    public void startComponent_runStudy_success() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        jw.setId(7L);
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");
        ComponentResult cr = newComponentResult(20L, comp, sr);

        IdCookieModel idCookie = mock(IdCookieModel.class);
        when(idCookie.getJatosRun()).thenReturn(JatosPublix.JatosRun.RUN_STUDY);
        when(idCookieService.getIdCookie(sr.getId())).thenReturn(idCookie);

        when(studyAssets.retrieveComponentHtmlFile("dir", "index.html")).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponent(comp, sr, null)).thenReturn(cr);

        Result res = publix.startComponent(fakeRequest().build(), sr, comp, null);

        assertEquals(OK, res.status());
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(any(), eq(jw), eq(study), eq(batch));
        verify(publixUtils).checkComponentBelongsToStudy(study, comp);
        verify(idCookieService).writeIdCookie(sr, cr, JatosPublix.JatosRun.RUN_STUDY);
    }

    @Test
    public void startComponent_runComponentStart_transitionsToFinished() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");
        ComponentResult cr = newComponentResult(20L, comp, sr);

        IdCookieModel idCookie = mock(IdCookieModel.class);
        when(idCookie.getJatosRun()).thenReturn(JatosPublix.JatosRun.RUN_COMPONENT_START);
        when(idCookieService.getIdCookie(sr.getId())).thenReturn(idCookie);

        when(studyAssets.retrieveComponentHtmlFile("dir", "index.html")).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponent(comp, sr, null)).thenReturn(cr);

        Result res = publix.startComponent(fakeRequest().build(), sr, comp, null);

        assertEquals(OK, res.status());
        verify(idCookieService).writeIdCookie(sr, cr, JatosPublix.JatosRun.RUN_COMPONENT_FINISHED);
    }

    @Test
    public void startComponent_runComponentFinished_nextDifferent_redirectsFinishStudy() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult srReal = newStudyResult(10L, "sr-uuid", study, batch, jw);
        StudyResult sr = spy(srReal);
        Component first = newComponent(3L, "first", "index.html");
        Component second = newComponent(4L, "second", "second.html");
        ComponentResult lastCr = newComponentResult(20L, first, srReal);
        when(sr.getLastComponentResult()).thenReturn(java.util.Optional.of(lastCr));

        IdCookieModel idCookie = mock(IdCookieModel.class);
        when(idCookie.getJatosRun()).thenReturn(JatosPublix.JatosRun.RUN_COMPONENT_FINISHED);
        when(idCookieService.getIdCookie(sr.getId())).thenReturn(idCookie);

        when(studyAssets.retrieveComponentHtmlFile(anyString(), anyString())).thenReturn(play.mvc.Results.ok().asScala());
        when(publixUtils.startComponent(second, sr, null)).thenReturn(newComponentResult(21L, second, sr));

        Result res = publix.startComponent(fakeRequest().build(), sr, second, null);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid/end"));
    }

    @Test
    public void startComponent_exception_finishesStudyUnsuccessful() throws Exception {
        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);
        Component comp = newComponent(3L, "c-uuid", "index.html");

        IdCookieModel idCookie = mock(IdCookieModel.class);
        when(idCookie.getJatosRun()).thenReturn(JatosPublix.JatosRun.RUN_STUDY);
        when(idCookieService.getIdCookie(sr.getId())).thenReturn(idCookie);

        when(publixUtils.startComponent(comp, sr, null)).thenThrow(new ForbiddenReloadException("reload"));

        Result res = publix.startComponent(fakeRequest().build(), sr, comp, null);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.contains("/publix/sr-uuid/end"));
        assertTrue(loc.contains("successful=false"));
    }

    // -------------------- abortStudy --------------------

    @Test
    public void abortStudy_nonAjax_redirectsToJatosUrl() throws Exception {
        helpersStatic.when(Helpers::isAjax).thenReturn(false);

        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.abortStudy(fakeRequest().build(), sr, "bye");

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.contains("/jatos/1"));
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(any(), eq(jw), eq(study), eq(batch));
        verify(publixUtils).abortStudy(any(), eq(sr));
        verify(groupAdministration).leaveGroup(sr);
        verify(idCookieService).discardIdCookie(sr.getId());
        verify(studyLogger).log(eq(study), any(), eq(jw));
    }

    @Test
    public void abortStudy_ajax_ok() throws Exception {
        helpersStatic.when(Helpers::isAjax).thenReturn(true);

        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.abortStudy(fakeRequest().build(), sr, null);
        assertEquals(OK, res.status());
    }

    // -------------------- finishStudy --------------------

    @Test
    public void finishStudy_nonAjax_redirectsToJatosUrl() throws Exception {
        helpersStatic.when(Helpers::isAjax).thenReturn(false);

        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.finishStudy(fakeRequest().build(), sr, true, null);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.contains("/jatos/1"));
        verify(studyAuthorisation).checkWorkerAllowedToDoStudy(any(), eq(jw), eq(study), eq(batch));
        verify(publixUtils).finishStudyResult(any(), any(), eq(sr));
        verify(groupAdministration).leaveGroup(sr);
        verify(idCookieService).discardIdCookie(sr.getId());
        verify(studyLogger).log(eq(study), any(), eq(jw));
    }

    @Test
    public void finishStudy_ajax_ok() throws Exception {
        helpersStatic.when(Helpers::isAjax).thenReturn(true);

        Study study = newStudy(1L, "dir");
        Batch batch = newBatch(2L, study);
        JatosWorker jw = new JatosWorker();
        StudyResult sr = newStudyResult(10L, "sr-uuid", study, batch, jw);

        Result res = publix.finishStudy(fakeRequest().build(), sr, false, "err");
        assertEquals(OK, res.status());
    }
}
