package services.publix;

import controllers.publix.workers.JatosPublix;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.*;
import general.common.Common;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.mvc.Http;
import services.publix.idcookie.IdCookieService;
import utils.common.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PublixUtils.
 */
public class PublixUtilsTest {

    private static MockedStatic<Common> commonStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonStatic.when(Common::getResultUploadsPath).thenReturn(tmp);
        commonStatic.when(Common::getStudyArchiveSuffix).thenReturn("jzip");
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    private ResultCreator resultCreator;
    private StudyResultDao studyResultDao;
    private ComponentDao componentDao;
    private ComponentResultDao componentResultDao;
    private UserDao userDao;
    private IOUtils ioUtils;

    private PublixUtils publixUtils;

    @Before
    public void setup() {
        IdCookieService idCookieService = mock(IdCookieService.class);
        GroupAdministration groupAdministration = mock(GroupAdministration.class);
        WorkerDao workerDao = mock(WorkerDao.class);
        StudyLogger studyLogger = mock(StudyLogger.class);
        resultCreator = mock(ResultCreator.class);
        studyResultDao = mock(StudyResultDao.class);
        componentDao = mock(ComponentDao.class);
        componentResultDao = mock(ComponentResultDao.class);
        userDao = mock(UserDao.class);
        ioUtils = mock(IOUtils.class);

        publixUtils = new PublixUtils(resultCreator, idCookieService, groupAdministration,
                studyResultDao, componentDao, componentResultDao, workerDao, userDao, studyLogger, ioUtils);
    }

    private static Study newStudyWithComponents(boolean linear, Component... components) {
        Study s = new Study();
        s.setId(1L);
        s.setLinearStudy(linear);
        for (Component c : components) {
            c.setStudy(s);
            s.getComponentList().add(c);
        }
        return s;
    }

    private static Component newComponent(long id, String title, boolean active, boolean reloadable) {
        Component c = new Component();
        c.setId(id);
        c.setTitle(title);
        c.setActive(active);
        c.setReloadable(reloadable);
        return c;
    }

    private static StudyResult newStudyResult(Study s) {
        StudyResult sr = new StudyResult();
        sr.setId(10L);
        sr.setStudy(s);
        sr.setStudyState(StudyState.PRE);
        return sr;
    }

    private static ComponentResult newComponentResult(StudyResult sr, Component c, ComponentState state) {
        ComponentResult cr = new ComponentResult();
        cr.setId(new Random().nextLong());
        cr.setStudyResult(sr);
        cr.setComponent(c);
        cr.setComponentState(state);
        sr.getComponentResultList().add(cr);
        return cr;
    }

    @Test
    public void retrieveFirstActiveComponent_returnsFirstActive() throws Exception {
        Component c1 = newComponent(1, "c1", false, true);
        Component c2 = newComponent(2, "c2", true, true);
        Study s = newStudyWithComponents(true, c1, c2);

        Component first = publixUtils.retrieveFirstActiveComponent(s);
        assertEquals(c2, first);
    }

    @Test(expected = NotFoundPublixException.class)
    public void retrieveFirstActiveComponent_throwsWhenNoneActive() throws Exception {
        Component c1 = newComponent(1, "c1", false, true);
        Component c2 = newComponent(2, "c2", false, true);
        Study s = newStudyWithComponents(true, c1, c2);
        publixUtils.retrieveFirstActiveComponent(s);
    }

    @Test
    public void retrieveComponent_success() throws Exception {
        Component c = newComponent(5, "x", true, true);
        Study s = new Study();
        s.setId(99L);
        c.setStudy(s);
        when(componentDao.findById(5L)).thenReturn(c);

        Component res = publixUtils.retrieveComponent(s, 5L);
        assertSame(c, res);
    }

    @Test(expected = NotFoundPublixException.class)
    public void retrieveComponent_notFound() throws Exception {
        Study s = new Study();
        s.setId(11L);
        when(componentDao.findById(123L)).thenReturn(null);
        publixUtils.retrieveComponent(s, 123L);
    }

    @Test(expected = BadRequestPublixException.class)
    public void retrieveComponent_wrongStudy() throws Exception {
        Study s = new Study(); s.setId(1L);
        Study other = new Study(); other.setId(2L);
        Component c = newComponent(5, "x", true, true);
        c.setStudy(other);
        when(componentDao.findById(5L)).thenReturn(c);
        publixUtils.retrieveComponent(s, 5L);
    }

    @Test(expected = ForbiddenPublixException.class)
    public void retrieveComponent_inactive() throws Exception {
        Study s = new Study(); s.setId(1L);
        Component c = newComponent(5, "x", false, true);
        c.setStudy(s);
        when(componentDao.findById(5L)).thenReturn(c);
        publixUtils.retrieveComponent(s, 5L);
    }

    @Test
    public void retrieveCurrentComponentResult_returnsPresentIfNotDone() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        newComponentResult(sr, c, ComponentState.STARTED);

        Optional<ComponentResult> current = publixUtils.retrieveCurrentComponentResult(sr);
        assertTrue(current.isPresent());
    }

    @Test
    public void retrieveCurrentComponentResult_returnsEmptyIfDone() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        newComponentResult(sr, c, ComponentState.FINISHED);

        Optional<ComponentResult> current = publixUtils.retrieveCurrentComponentResult(sr);
        assertFalse(current.isPresent());
    }

    @Test
    public void retrieveStartedComponentResult_returnsExistingCurrent() throws Exception {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr = newComponentResult(sr, c, ComponentState.STARTED);

        ComponentResult res = publixUtils.retrieveStartedComponentResult(c, sr);
        assertSame(cr, res);
        verifyNoInteractions(resultCreator);
    }

    @Test
    public void retrieveStartedComponentResult_startsNewIfNone() throws Exception {
        Study s = new Study();
        Component c = newComponent(1, "a", true, true);
        StudyResult sr = newStudyResult(s);
        ComponentResult created = new ComponentResult();
        when(resultCreator.createComponentResult(sr, c)).thenReturn(created);

        ComponentResult res = publixUtils.retrieveStartedComponentResult(c, sr);
        assertSame(created, res);
        verify(resultCreator).createComponentResult(sr, c);
    }

    @Test
    public void startComponent_allowsReloadWhenReloadable() throws Exception {
        Component c = newComponent(1, "a", true, true);
        Study s = newStudyWithComponents(true, c);
        StudyResult sr = newStudyResult(s);
        ComponentResult last = newComponentResult(sr, c, ComponentState.STARTED);

        ComponentResult created = new ComponentResult();
        when(resultCreator.createComponentResult(sr, c)).thenReturn(created);

        ComponentResult res = publixUtils.startComponent(c, sr, "msg");
        assertSame(created, res);
        // Last should be set to RELOADED and updated
        assertEquals(ComponentState.RELOADED, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).update(last);
    }

    @Test
    public void startComponent_forbidsReloadWhenNotReloadable() {
        Component c = newComponent(1, "a", true, false);
        Study s = newStudyWithComponents(true, c);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.STARTED);
        newComponentResult(sr, c, ComponentState.STARTED);

        try {
            publixUtils.startComponent(c, sr, "msg");
            fail("Expected ForbiddenReloadException");
        } catch (ForbiddenReloadException e) {
            // ok
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        // Last should be set to FAIL and updated
        ComponentResult last = sr.getLastComponentResult().get();
        assertEquals(ComponentState.FAIL, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).update(last);
    }

    @Test
    public void startComponent_forbidsNonLinearFlow() {
        Component c1 = newComponent(1, "a", true, true);
        Component c2 = newComponent(2, "b", true, true);
        Study s = newStudyWithComponents(true, c1, c2); // linear
        StudyResult sr = newStudyResult(s);
        newComponentResult(sr, c2, ComponentState.STARTED); // last was c2

        try {
            publixUtils.startComponent(c1, sr, "x"); // try to go back to c1
            fail("Expected ForbiddenNonLinearFlowException");
        } catch (ForbiddenNonLinearFlowException e) {
            // ok
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        ComponentResult last = sr.getLastComponentResult().get();
        assertEquals(ComponentState.FAIL, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).update(last);
    }

    @Test
    public void finishStudyResult_successful() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Worker worker = mock(Worker.class);
        when(worker.generateConfirmationCode()).thenReturn("CONF");
        sr.setWorker(worker);
        Component c = newComponent(1, "a", true, true);
        ComponentResult current = newComponentResult(sr, c, ComponentState.STARTED);
        ComponentResult other = newComponentResult(sr, c, ComponentState.STARTED);

        String code = publixUtils.finishStudyResult(true, "done", sr);
        assertEquals("CONF", code);
        assertEquals(StudyState.FINISHED, sr.getStudyState());
        assertEquals("done", sr.getMessage());
        assertNull(sr.getStudySessionData());
        assertNotNull(sr.getEndDate());
        // Component results updated and finished
        assertEquals(ComponentState.FINISHED, current.getComponentState());
        assertEquals(ComponentState.FINISHED, other.getComponentState());
        verify(componentResultDao, atLeast(2)).update(any(ComponentResult.class));
        verify(studyResultDao).update(sr);
    }

    @Test
    public void finishStudyResult_unsuccessful() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Worker worker = mock(Worker.class);
        when(worker.generateConfirmationCode()).thenReturn("CONF");
        sr.setWorker(worker);
        Component c = newComponent(1, "a", true, true);
        ComponentResult current = newComponentResult(sr, c, ComponentState.STARTED);

        String code = publixUtils.finishStudyResult(false, "fail", sr);
        assertNull(code);
        assertEquals(StudyState.FAIL, sr.getStudyState());
        assertEquals(ComponentState.FAIL, current.getComponentState());
        verify(studyResultDao).update(sr);
    }

    @Test
    public void abortStudy_setsAbortedAndPurgesAndRemoves() throws IOException {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        sr.setStudySessionData("x");
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr1 = newComponentResult(sr, c, ComponentState.STARTED);
        ComponentResult cr2 = newComponentResult(sr, c, ComponentState.STARTED);

        publixUtils.abortStudy("bye", sr);

        assertEquals(StudyState.ABORTED, sr.getStudyState());
        assertEquals("bye", sr.getMessage());
        assertNull(sr.getStudySessionData());
        assertNotNull(sr.getEndDate());
        assertEquals(ComponentState.ABORTED, cr1.getComponentState());
        assertEquals(ComponentState.ABORTED, cr2.getComponentState());
        verify(componentResultDao, atLeast(2)).purgeData(anyLong());
        verify(componentResultDao, atLeast(2)).update(any(ComponentResult.class));
        verify(ioUtils).removeResultUploadsDir(sr.getId());
        verify(studyResultDao).update(sr);
    }

    @Test
    public void setPreStudyState_transitionsWhenMovedBeyondFirst() throws Exception {
        Component c1 = newComponent(1, "a", true, true);
        Component c2 = newComponent(2, "b", true, true);
        Study s = newStudyWithComponents(true, c1, c2);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.PRE);
        ComponentResult cr = newComponentResult(sr, c2, ComponentState.STARTED);

        publixUtils.setPreStudyState(cr);
        assertEquals(StudyState.STARTED, sr.getStudyState());
        verify(studyResultDao).update(sr);
    }

    @Test
    public void setPreStudyState_keepsPreOnFirstComponent() throws Exception {
        Component c1 = newComponent(1, "a", true, true);
        Study s = newStudyWithComponents(true, c1);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.PRE);
        ComponentResult cr = newComponentResult(sr, c1, ComponentState.STARTED);

        publixUtils.setPreStudyState(cr);
        assertEquals(StudyState.PRE, sr.getStudyState());
        verify(studyResultDao).update(sr);
    }

    @Test
    public void isFirstComponentInPreviewStudy_trueOnlyOnFirstAndPre() throws Exception {
        Component c1 = newComponent(1, "a", true, true);
        Component c2 = newComponent(2, "b", true, true);
        Study s = newStudyWithComponents(true, c1, c2);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.PRE);

        ComponentResult cr1 = newComponentResult(sr, c1, ComponentState.STARTED);
        assertTrue(publixUtils.isFirstComponentInPreviewStudy(cr1));

        ComponentResult cr2 = newComponentResult(sr, c2, ComponentState.STARTED);
        assertFalse(publixUtils.isFirstComponentInPreviewStudy(cr2));

        sr.setStudyState(StudyState.STARTED);
        assertFalse(publixUtils.isFirstComponentInPreviewStudy(cr1));
    }

    @Test
    public void setUrlQueryParameter_extractsAndStoresJson() {
        StudyResult sr = new StudyResult();
        Map<String, String[]> map = new HashMap<>();
        map.put("a", new String[]{"1"});
        map.put("b", new String[]{"x"});
        Http.Request request = Mockito.mock(Http.Request.class);
        when(request.queryString()).thenReturn(map);

        publixUtils.setUrlQueryParameter(request, sr);

        String json = sr.getUrlQueryParameters();
        assertTrue(json.contains("\"a\":\"1\""));
        assertTrue(json.contains("\"b\":\"x\""));
    }

    @Test
    public void retrieveSignedinUser_successAndFailures() throws Exception {
        Http.Session session = mock(Http.Session.class);
        when(session.getOptional(JatosPublix.SESSION_USERNAME)).thenReturn(Optional.of("alice"));

        Http.Request request = mock(Http.Request.class);
        when(request.session()).thenReturn(session);

        User user = new User();
        user.setUsername("alice");
        when(userDao.findByUsername("alice")).thenReturn(user);

        User res = publixUtils.retrieveSignedinUser(request);
        assertSame(user, res);

        // No username in session
        when(session.getOptional(JatosPublix.SESSION_USERNAME)).thenReturn(Optional.empty());
        try {
            publixUtils.retrieveSignedinUser(request);
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenPublixException e) {
            // expected
        }

        // User not found
        when(session.getOptional(JatosPublix.SESSION_USERNAME)).thenReturn(Optional.of("bob"));
        when(userDao.findByUsername("bob")).thenReturn(null);
        try {
            publixUtils.retrieveSignedinUser(request);
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenPublixException e) {
            // expected
        }
    }

    @Test
    public void fetchJatosRunFromSession_parsesValueOrThrows() throws Exception {
        Http.Session session = mock(Http.Session.class);
        // First time return "RUN_STUDY", second "INVALID", and third Optional.empty()
        when(session.getOptional("jatos_run")).thenReturn(Optional.of("RUN_STUDY"))
                .thenReturn(Optional.of("INVALID")).thenReturn(Optional.empty());

        // Valid
        JatosPublix.JatosRun run = publixUtils.fetchJatosRunFromSession(session);
        assertEquals(JatosPublix.JatosRun.RUN_STUDY, run);

        // Malformed
        try {
            publixUtils.fetchJatosRunFromSession(session);
            fail("Expected BadRequestPublixException");
        } catch (BadRequestPublixException e) {
            // expected
        }

        // Missing
        try {
            publixUtils.fetchJatosRunFromSession(session);
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenPublixException e) {
            // expected
        }
    }

    @Test
    public void retrieveLastUploadedResultFile_returnsLastExisting() throws Exception {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr1 = newComponentResult(sr, c, ComponentState.FINISHED);
        ComponentResult cr2 = newComponentResult(sr, c, ComponentState.FINISHED);

        // The list is [cr1, cr2]; logic reverses it and checks cr2 first
        File f1 = File.createTempFile("jatos-test1", ".txt");
        File f2 = File.createTempFile("jatos-test2", ".txt");
        // Simulate first check returns non-existent, second exists
        when(ioUtils.getResultUploadFileSecurely(sr.getId(), cr1.getId(), "x.txt")).thenReturn(f1);
        when(ioUtils.getResultUploadFileSecurely(sr.getId(), cr2.getId(), "x.txt")).thenReturn(f2);
        // Delete f1 to make exists() false
        //noinspection ResultOfMethodCallIgnored
        f2.delete();

        Optional<File> res = publixUtils.retrieveLastUploadedResultFile(sr, c, "x.txt");
        assertTrue(res.isPresent());
        assertEquals(f1, res.get());
    }
}
