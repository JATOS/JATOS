package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import general.common.Common;
import models.common.*;
import models.common.workers.GeneralSingleWorker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import testutils.common.ContextMocker;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IdCookieServiceTest {

    private IdCookieAccessor accessor;
    private IdCookieService service;

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Before
    public void setup() {
        ContextMocker.mock(); // ensure RequestScope/Context exists for internals
        accessor = mock(IdCookieAccessor.class);
        service = new IdCookieService(accessor);
    }

    @Test
    public void has_and_get_IdCookie() throws Exception {
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel m = new IdCookieModel();
        m.setStudyResultId(5L);
        m.setName(IdCookieModel.ID_COOKIE_NAME + "_5");
        col.add(m);
        when(accessor.extract()).thenReturn(col);

        assertTrue(service.hasIdCookie(5L));
        assertSame(m, service.getIdCookie(5L));
    }

    @Test(expected = BadRequestPublixException.class)
    public void getIdCookie_throws_when_missing() throws Exception {
        when(accessor.extract()).thenReturn(new IdCookieCollection());
        service.getIdCookie(99L);
    }

    @Test
    public void oneIdCookieHasThisStudyAssets_checks_all() throws Exception {
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel a = new IdCookieModel(); a.setStudyResultId(1L); a.setName("n1"); a.setStudyAssets("a");
        IdCookieModel b = new IdCookieModel(); b.setStudyResultId(2L); b.setName("n2"); b.setStudyAssets("b");
        col.add(a); col.add(b);
        when(accessor.extract()).thenReturn(col);
        assertTrue(service.oneIdCookieHasThisStudyAssets("a"));
        assertFalse(service.oneIdCookieHasThisStudyAssets("x"));
    }

    @Test
    public void writeIdCookie_reuses_existing_name_otherwise_creates_new() throws Exception {
        // Existing cookie for studyResult 7 -> reuse its name
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel existing = new IdCookieModel();
        existing.setStudyResultId(7L); existing.setName("JATOS_ID_7"); col.add(existing);
        when(accessor.extract()).thenReturn(col);

        Study study = new Study(); study.setId(1L); study.setDirName("dir");
        Batch batch = new Batch(); batch.setId(2L);
        StudyResult sr = new StudyResult(); sr.setId(7L); sr.setUuid("uuid-7"); sr.setStudy(study); sr.setBatch(batch);
        GeneralSingleWorker w = new GeneralSingleWorker(); w.setId(9L); sr.setWorker(w);
        Component comp = new Component(); comp.setId(3L);
        study.getComponentList().add(comp);
        ComponentResult cr = new ComponentResult(); cr.setId(4L); cr.setComponent(comp);

        // Use ArgumentCaptor to capture the IdCookieModel that is passed to the accessor's write method
        ArgumentCaptor<IdCookieModel> captor = ArgumentCaptor.forClass(IdCookieModel.class);
        service.writeIdCookie(sr, cr, JatosRun.RUN_STUDY);
        verify(accessor).write(captor.capture());
        IdCookieModel written = captor.getValue();
        assertEquals("JATOS_ID_7", written.getName());
        assertEquals(Long.valueOf(1L), written.getStudyId());
        assertEquals(Long.valueOf(2L), written.getBatchId());
        assertEquals(Long.valueOf(3L), written.getComponentId());
        assertEquals(Long.valueOf(4L), written.getComponentResultId());
        assertEquals(study.getComponentPosition(comp), written.getComponentPosition());
        assertEquals(Long.valueOf(9L), written.getWorkerId());
        assertEquals("GeneralSingle", written.getWorkerType());
        assertEquals(JatosRun.RUN_STUDY, written.getJatosRun());
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void maxIdCookiesReached_reflects_collection_isFull() throws Exception {
        // set limit to 1 and fill collection with 1 item
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(1);
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel m = new IdCookieModel(); m.setStudyResultId(1L); m.setName("JATOS_ID_1");
        col.put(m);
        when(accessor.extract()).thenReturn(col);
        assertTrue(service.maxIdCookiesReached());
    }

    @Test
    public void oldest_cookie_detection_and_id_extraction() throws Exception {
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel a = new IdCookieModel(); a.setStudyResultId(1L); a.setName("n1"); a.setCreationTime(200L); col.add(a);
        IdCookieModel b = new IdCookieModel(); b.setStudyResultId(2L); b.setName("n2"); b.setCreationTime(100L); col.add(b);
        when(accessor.extract()).thenReturn(col);
        assertSame(b, service.getOldestIdCookie());
        assertEquals(Long.valueOf(2L), service.getStudyResultIdFromOldestIdCookie());
    }

    @Test
    public void discardIdCookie_delegates_to_accessor() throws Exception {
        service.discardIdCookie(77L);
        verify(accessor).discard(77L);
    }
}
