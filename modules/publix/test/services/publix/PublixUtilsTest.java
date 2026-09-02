package services.publix;

import controllers.publix.workers.JatosPublix;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import general.common.Common;
import general.common.StudyLogger;
import group.GroupAdministration;
import http.common.Http.Context;
import json.common.DefaultJson;
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
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.test.Helpers;
import services.publix.idcookie.IdCookieService;
import testutils.publix.JPAMocker;
import utils.common.IOUtils;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static controllers.publix.workers.JatosPublix.SESSION_USERNAME;
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
        resultCreator = mock(ResultCreator.class);
        IdCookieService idCookieService = mock(IdCookieService.class);
        GroupAdministration groupAdministration = mock(GroupAdministration.class);
        studyResultDao = mock(StudyResultDao.class);
        componentDao = mock(ComponentDao.class);
        componentResultDao = mock(ComponentResultDao.class);
        WorkerDao workerDao = mock(WorkerDao.class);
        userDao = mock(UserDao.class);
        StudyLogger studyLogger = mock(StudyLogger.class);
        ioUtils = mock(IOUtils.class);
        DefaultJson defaultJson = new DefaultJson();

        publixUtils = new PublixUtils(resultCreator, idCookieService, groupAdministration,
                studyResultDao, componentDao, componentResultDao, workerDao, userDao, studyLogger, ioUtils, defaultJson);

        EntityManager entityManager = Mockito.mock(EntityManager.class);
        JPAMocker.mockDaoTransactions(entityManager, studyResultDao, componentDao, componentResultDao, workerDao, userDao);
    }

    @SuppressWarnings("SameParameterValue")
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
    public void retrieveFirstActiveComponent_returnsFirstActive() {
        Component c1 = newComponent(1, "c1", false, true);
        Component c2 = newComponent(2, "c2", true, true);
        Study s = newStudyWithComponents(true, c1, c2);

        Component first = publixUtils.retrieveFirstActiveComponent(s);
        assertEquals(c2, first);
    }

    @Test(expected = NotFoundException.class)
    public void retrieveFirstActiveComponent_throwsWhenNoneActive() {
        Component c1 = newComponent(1, "c1", false, true);
        Component c2 = newComponent(2, "c2", false, true);
        Study s = newStudyWithComponents(true, c1, c2);
        publixUtils.retrieveFirstActiveComponent(s);
    }

    @Test
    public void retrieveComponent_success() {
        Component c = newComponent(5, "x", true, true);
        Study s = new Study();
        s.setId(99L);
        c.setStudy(s);
        when(componentDao.findById(5L)).thenReturn(c);

        Component res = publixUtils.retrieveComponent(s, 5L);
        assertSame(c, res);
    }

    @Test(expected = NotFoundException.class)
    public void retrieveComponent_notFound() {
        Study s = new Study();
        s.setId(11L);
        when(componentDao.findById(123L)).thenReturn(null);
        publixUtils.retrieveComponent(s, 123L);
    }

    @Test(expected = BadRequestException.class)
    public void retrieveComponent_wrongStudy() {
        Study s = new Study();
        s.setId(1L);
        Study other = new Study();
        other.setId(2L);
        Component c = newComponent(5, "x", true, true);
        c.setStudy(other);
        when(componentDao.findById(5L)).thenReturn(c);
        publixUtils.retrieveComponent(s, 5L);
    }

    @Test(expected = ForbiddenException.class)
    public void retrieveComponent_inactive() {
        Study s = new Study();
        s.setId(1L);
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
        ComponentResult cr = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(cr));

        Optional<ComponentResult> current = publixUtils.retrieveCurrentComponentResult(sr);
        assertTrue(current.isPresent());
    }

    @Test
    public void retrieveCurrentComponentResult_returnsEmptyIfDone() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr = newComponentResult(sr, c, ComponentState.FINISHED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(cr));

        Optional<ComponentResult> current = publixUtils.retrieveCurrentComponentResult(sr);
        assertFalse(current.isPresent());
    }

    @Test
    public void retrieveStartedComponentResult_returnsExistingCurrent() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(cr));

        ComponentResult res = publixUtils.retrieveStartedComponentResult(c, sr);
        assertSame(cr, res);
        verifyNoInteractions(resultCreator);
    }

    @Test
    public void retrieveStartedComponentResult_startsNewIfNone() {
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
    public void startComponent_Run_allowsReloadWhenReloadable() {
        Component c = newComponent(1, "a", true, true);
        Study s = newStudyWithComponents(true, c);
        StudyResult sr = newStudyResult(s);
        ComponentResult last = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(last));

        ComponentResult created = new ComponentResult();
        when(resultCreator.createComponentResult(sr, c)).thenReturn(created);

        ComponentResult res = publixUtils.startComponentRun(c, sr, "msg");
        assertSame(created, res);
        // Last should be set to RELOADED and updated
        assertEquals(ComponentState.RELOADED, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).merge(last);
    }

    @Test
    public void startComponent_Run_forbidsReloadWhenNotReloadable() {
        Component c = newComponent(1, "a", true, false);
        Study s = newStudyWithComponents(true, c);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.STARTED);
        ComponentResult cr = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(cr));

        try {
            publixUtils.startComponentRun(c, sr, "msg");
            fail("Expected ForbiddenReloadException");
        } catch (ForbiddenReloadException e) {
            // ok
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        // Last should be set to FAIL and updated
        ComponentResult last = lastElement(sr.getComponentResultList()).orElseThrow();
        componentResultDao.findLastByStudyResult(sr);
        assertEquals(ComponentState.FAIL, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).merge(last);
    }

    @Test
    public void startComponent_Run_forbidsNonLinearFlow() {
        Component c1 = newComponent(1, "a", true, true);
        Component c2 = newComponent(2, "b", true, true);
        Study s = newStudyWithComponents(true, c1, c2); // linear
        StudyResult sr = newStudyResult(s);
        ComponentResult cr = newComponentResult(sr, c2, ComponentState.STARTED); // last was c2

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(cr));

        try {
            publixUtils.startComponentRun(c1, sr, "x"); // try to go back to c1
            fail("Expected ForbiddenNonLinearFlowException");
        } catch (ForbiddenNonLinearFlowException e) {
            // ok
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        ComponentResult last = lastElement(sr.getComponentResultList()).orElseThrow();
        assertEquals(ComponentState.FAIL, last.getComponentState());
        verify(componentResultDao, atLeastOnce()).merge(last);
    }

    @Test
    public void finishStudyRun_successful() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Worker worker = mock(Worker.class);
        when(worker.generateConfirmationCode()).thenReturn("CONF");
        sr.setWorker(worker);
        Component c = newComponent(1, "a", true, true);
        ComponentResult current = newComponentResult(sr, c, ComponentState.STARTED);
        ComponentResult other = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(current));

        String code = publixUtils.finishStudyRun(true, "done", sr);
        assertEquals("CONF", code);
        assertEquals(StudyState.FINISHED, sr.getStudyState());
        assertEquals("done", sr.getMessage());
        assertNull(sr.getStudySessionData());
        assertNotNull(sr.getEndDate());
        // Component results updated and finished
        assertEquals(ComponentState.FINISHED, current.getComponentState());
        assertEquals(ComponentState.FINISHED, other.getComponentState());
        verify(componentResultDao, atLeast(2)).merge(any(ComponentResult.class));
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void finishStudyRun_unsuccessful() {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        Worker worker = mock(Worker.class);
        when(worker.generateConfirmationCode()).thenReturn("CONF");
        sr.setWorker(worker);
        Component c = newComponent(1, "a", true, true);
        ComponentResult current = newComponentResult(sr, c, ComponentState.STARTED);

        when(componentResultDao.findLastByStudyResult(sr)).thenReturn(Optional.of(current));

        String code = publixUtils.finishStudyRun(false, "fail", sr);
        assertNull(code);
        assertEquals(StudyState.FAIL, sr.getStudyState());
        assertEquals(ComponentState.FAIL, current.getComponentState());
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void abortStudy_Run_setsAbortedAndPurgesAndRemoves() throws IOException {
        Study s = new Study();
        StudyResult sr = newStudyResult(s);
        sr.setStudySessionData("x");
        Component c = newComponent(1, "a", true, true);
        ComponentResult cr1 = newComponentResult(sr, c, ComponentState.STARTED);
        ComponentResult cr2 = newComponentResult(sr, c, ComponentState.STARTED);

        publixUtils.abortStudyResult("bye", sr);

        assertEquals(StudyState.ABORTED, sr.getStudyState());
        assertEquals("bye", sr.getMessage());
        assertNull(sr.getStudySessionData());
        assertNotNull(sr.getEndDate());
        assertEquals(ComponentState.ABORTED, cr1.getComponentState());
        assertEquals(ComponentState.ABORTED, cr2.getComponentState());
        verify(componentResultDao, atLeast(2)).purgeData(anyLong());
        verify(componentResultDao, atLeast(2)).merge(any(ComponentResult.class));
        verify(ioUtils).removeResultUploadsDir(sr.getId());
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void setPreStudyState_transitionsWhenMovedBeyondFirst() {
        Component c1 = newComponent(1, "a", true, true);
        Component c2 = newComponent(2, "b", true, true);
        Study s = newStudyWithComponents(true, c1, c2);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.PRE);
        ComponentResult cr = newComponentResult(sr, c2, ComponentState.STARTED);

        publixUtils.setPreStudyState(cr);
        assertEquals(StudyState.STARTED, sr.getStudyState());
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void setPreStudyState_keepsPreOnFirstComponent() {
        Component c1 = newComponent(1, "a", true, true);
        Study s = newStudyWithComponents(true, c1);
        StudyResult sr = newStudyResult(s);
        sr.setStudyState(StudyState.PRE);
        ComponentResult cr = newComponentResult(sr, c1, ComponentState.STARTED);

        publixUtils.setPreStudyState(cr);
        assertEquals(StudyState.PRE, sr.getStudyState());
        verify(studyResultDao).merge(sr);
    }

    @Test
    public void isFirstComponentInPreviewStudy_trueOnlyOnFirstAndPre() {
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

        Http.Request request = Helpers.fakeRequest("GET", "/jatos?a=1&b=x").build();
        Context.setCurrent(new Context(request));

        publixUtils.setUrlQueryParameter(sr);

        String json = sr.getUrlQueryParameters();
        assertTrue(json.contains("\"a\":\"1\""));
        assertTrue(json.contains("\"b\":\"x\""));
    }

    @Test
    public void retrieveSignedinUser_successAndFailures() {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().response().putSession(SESSION_USERNAME, "alice");

        User user = new User();
        user.setUsername("alice");
        when(userDao.findByUsername("alice")).thenReturn(user);

        User res = publixUtils.retrieveSignedinUser();
        assertSame(user, res);

        // No username in session
        Context.current().response().removeSession(SESSION_USERNAME);
        try {
            publixUtils.retrieveSignedinUser();
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenException e) {
            // expected
        }

        // User not found
        Context.current().response().putSession(SESSION_USERNAME, "bob");
        when(userDao.findByUsername("bob")).thenReturn(null);
        try {
            publixUtils.retrieveSignedinUser();
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenException e) {
            // expected
        }
    }

    @Test
    public void fetchJatosRunFromSession_parsesValueOrThrows() {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));

        // Valid
        Context.current().response().putSession("jatos_run", "RUN_STUDY");
        JatosPublix.JatosRun run = publixUtils.fetchJatosRunFromSession();
        assertEquals(JatosPublix.JatosRun.RUN_STUDY, run);

        // Malformed
        Context.current().response().putSession("jatos_run", "INVALID");
        try {
            publixUtils.fetchJatosRunFromSession();
            fail("Expected BadRequestPublixException");
        } catch (BadRequestException e) {
            // expected
        }

        // Missing
        Context.current().response().removeSession("jatos_run");
        try {
            publixUtils.fetchJatosRunFromSession();
            fail("Expected ForbiddenPublixException");
        } catch (ForbiddenException e) {
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
        when(componentResultDao.findIdsByStudyResultAndComponent(sr.getId(), c))
                .thenReturn(new ArrayList<>(List.of(cr1.getId(), cr2.getId())));

        // The list is [cr1, cr2]; logic reverses it and checks cr2 first
        Path f1 = Files.createTempFile("jatos-test1", ".txt");
        Path f2 = Files.createTempFile("jatos-test2", ".txt");
        // Simulate first check returns non-existent, second exists
        when(ioUtils.getResultUploadFileSecurely(sr.getId(), cr1.getId(), "x.txt")).thenReturn(f1);
        when(ioUtils.getResultUploadFileSecurely(sr.getId(), cr2.getId(), "x.txt")).thenReturn(f2);
        // Delete f1 to make exists() false
        Files.deleteIfExists(f2);

        Optional<Path> res = publixUtils.retrieveLastUploadedResultFile(sr, c, "x.txt");
        assertTrue(res.isPresent());
        assertEquals(f1, res.get());
    }

    public static <T> Optional<T> lastElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }
}
