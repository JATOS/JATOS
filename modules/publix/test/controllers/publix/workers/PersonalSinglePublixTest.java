package controllers.publix.workers;

import controllers.publix.PersonalSingleGroupChannel;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.PersonalSingleWorker;
import org.junit.Before;
import org.junit.Test;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalSingleStudyAuthorisation;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.test.Helpers.*;

/**
 * Unit tests for PersonalSinglePublix methods (class-local logic only).
 */
public class PersonalSinglePublixTest {

    private PublixUtils publixUtils;
    private PersonalSingleStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private PersonalSingleGroupChannel groupChannel;
    private IdCookieService idCookieService;
    private StudyAssets studyAssets;
    private StudyLogger studyLogger;

    private PersonalSinglePublix publix;

    private final JPAApi jpa = mock(JPAApi.class);

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(PersonalSingleStudyAuthorisation.class);
        resultCreator = mock(ResultCreator.class);
        groupChannel = mock(PersonalSingleGroupChannel.class);
        idCookieService = mock(IdCookieService.class);
        studyAssets = mock(StudyAssets.class);
        studyLogger = mock(StudyLogger.class);
        PublixErrorMessages errorMessages = mock(PublixErrorMessages.class);
        JsonUtils jsonUtils = mock(JsonUtils.class);
        ComponentResultDao componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed here

        publix = new PersonalSinglePublix(jpa, publixUtils, studyAuthorisation, resultCreator, groupChannel,
                idCookieService, errorMessages, studyAssets, jsonUtils, componentResultDao, studyResultDao,
                studyLogger, ioUtils);
    }

    private static Study newStudy(long id) {
        Study s = new Study();
        s.setId(id);
        return s;
        }

    private static Batch newBatch(long id, Study study) {
        Batch b = new Batch();
        b.setId(id);
        b.setStudy(study);
        return b;
    }

    private static PersonalSingleWorker newWorker(long id) {
        PersonalSingleWorker w = mock(PersonalSingleWorker.class);
        when(w.getId()).thenReturn(id);
        return w;
    }

    private static StudyLink newStudyLink(Batch batch, PersonalSingleWorker worker) {
        StudyLink sl = new StudyLink();
        sl.setBatch(batch);
        sl.setStudyCode("code-ps");
        sl.setWorker(worker);
        sl.setWorkerType(PersonalSingleWorker.WORKER_TYPE);
        return sl;
    }

    private static StudyResult newStudyResult(long id, String uuid, Study study, Batch batch, PersonalSingleWorker worker) {
        StudyResult sr = new StudyResult();
        sr.setId(id);
        sr.setUuid(uuid);
        sr.setStudy(study);
        sr.setBatch(batch);
        sr.setWorker(worker);
        return sr;
    }

    private static Component newComponent(String uuid) {
        Component c = new Component();
        c.setUuid(uuid);
        return c;
    }

    // -------------------- startStudy --------------------

    @Test
    public void startStudy_firstCall_createsStudyResult_andRedirects() throws Exception {
        Study study = newStudy(1L);
        Batch batch = newBatch(2L, study);
        PersonalSingleWorker worker = newWorker(3L);
        StudyLink sl = newStudyLink(batch, worker);
        Http.Request request = fakeRequest().build();

        // No previous StudyResult
        when(worker.getLastStudyResult()).thenReturn(Optional.empty());
        Component first = newComponent("comp-uuid-1");
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(first);

        StudyResult sr = newStudyResult(10L, "sr-uuid-1", study, batch, worker);
        when(resultCreator.createStudyResult(sl, worker)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid-1/comp-uuid-1/start"));

        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(any(), eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResult();
        verify(resultCreator).createStudyResult(sl, worker);
        verify(idCookieService).writeIdCookie(sr);
        verify(publixUtils).setUrlQueryParameter(request, sr);
        verify(studyLogger).log(eq(sl), contains("Started study run"), eq(worker));
    }

    @Test
    public void startStudy_existingStudyResult_withCookie_doesNotFinishOldest() throws Exception {
        Study study = newStudy(11L);
        Batch batch = newBatch(12L, study);
        PersonalSingleWorker worker = newWorker(13L);
        StudyLink sl = newStudyLink(batch, worker);
        Http.Request request = fakeRequest().build();

        StudyResult existing = newStudyResult(20L, "sr-uuid-2", study, batch, worker);
        when(worker.getLastStudyResult()).thenReturn(Optional.of(existing));
        when(idCookieService.hasIdCookie(existing.getId())).thenReturn(true);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(newComponent("comp-uuid-2"));

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid-2/comp-uuid-2/start"));

        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(any(), eq(worker), eq(study), eq(batch));
        verify(publixUtils, never()).finishOldestStudyResult();
        verify(idCookieService).writeIdCookie(existing);
        verify(publixUtils).setUrlQueryParameter(request, existing);
        verifyNoInteractions(resultCreator);
    }

    @Test
    public void startStudy_existingStudyResult_missingCookie_finishesOldest() throws Exception {
        Study study = newStudy(21L);
        Batch batch = newBatch(22L, study);
        PersonalSingleWorker worker = newWorker(23L);
        StudyLink sl = newStudyLink(batch, worker);
        Http.Request request = fakeRequest().build();

        StudyResult existing = newStudyResult(30L, "sr-uuid-3", study, batch, worker);
        when(worker.getLastStudyResult()).thenReturn(Optional.of(existing));
        when(idCookieService.hasIdCookie(existing.getId())).thenReturn(false);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(newComponent("comp-uuid-3"));

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid-3/comp-uuid-3/start"));

        verify(publixUtils).finishOldestStudyResult();
        verify(idCookieService).writeIdCookie(existing);
        verifyNoInteractions(resultCreator);
    }
}
