package controllers.publix.workers;

import controllers.publix.GeneralSingleGroupChannel;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import org.junit.Before;
import org.junit.Test;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralSingleCookieService;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.test.Helpers.SEE_OTHER;
import static play.test.Helpers.fakeRequest;

/**
 * Unit tests for GeneralSinglePublix methods (class-local logic only).
 */
public class GeneralSinglePublixTest {

    private PublixUtils publixUtils;
    private GeneralSingleStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private WorkerCreator workerCreator;
    private IdCookieService idCookieService;
    private GeneralSingleCookieService generalSingleCookieService;
    private StudyLogger studyLogger;

    private GeneralSinglePublix publix;

    private final JPAApi jpa = mock(JPAApi.class);

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(GeneralSingleStudyAuthorisation.class);
        resultCreator = mock(ResultCreator.class);
        workerCreator = mock(WorkerCreator.class);
        GeneralSingleGroupChannel groupChannel = mock(GeneralSingleGroupChannel.class);
        idCookieService = mock(IdCookieService.class);
        generalSingleCookieService = mock(GeneralSingleCookieService.class);
        StudyAssets studyAssets = mock(StudyAssets.class);
        studyLogger = mock(StudyLogger.class);
        PublixErrorMessages errorMessages = mock(PublixErrorMessages.class);
        JsonUtils jsonUtils = mock(JsonUtils.class);
        ComponentResultDao componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed here

        publix = new GeneralSinglePublix(jpa, publixUtils, studyAuthorisation, resultCreator, workerCreator,
                groupChannel, idCookieService, generalSingleCookieService, errorMessages, studyAssets, jsonUtils,
                componentResultDao, studyResultDao, studyLogger, ioUtils);
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

    private static GeneralSingleWorker newGSWorker(long id) {
        GeneralSingleWorker w = mock(GeneralSingleWorker.class);
        when(w.getId()).thenReturn(id);
        return w;
    }

    private static StudyLink newStudyLink(Batch batch) {
        StudyLink sl = new StudyLink();
        sl.setBatch(batch);
        sl.setStudyCode("code-gs");
        sl.setWorkerType(GeneralSingleWorker.WORKER_TYPE);
        return sl;
    }

    private static StudyResult newStudyResult(long id, String uuid, Study study, Batch batch, Worker worker) {
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
    public void startStudy_firstCall_noWorkerCookie_createsWorkerAndStudyResult_andRedirects() throws Exception {
        Study study = newStudy(1L);
        Batch batch = newBatch(2L, study);
        GeneralSingleWorker worker = newGSWorker(3L);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        // No worker id cookie for this study
        when(generalSingleCookieService.fetchWorkerIdByStudy(study)).thenReturn(null);

        when(workerCreator.createAndPersistGeneralSingleWorker(batch)).thenReturn(worker);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(newComponent("comp-uuid-1"));

        StudyResult sr = newStudyResult(10L, "sr-uuid-1", study, batch, worker);
        when(resultCreator.createStudyResult(sl, worker)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid-1/comp-uuid-1/start"));

        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(any(), eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResult();
        verify(resultCreator).createStudyResult(sl, worker);
        verify(generalSingleCookieService).set(study, worker);
        verify(idCookieService).writeIdCookie(sr);
        verify(publixUtils).setUrlQueryParameter(request, sr);
        verify(studyLogger).log(eq(sl), contains("Started study run"), eq(worker));
    }

    @Test
    public void startStudy_previewAllowed_withWorkerCookie_andExistingIdCookie_doesNotFinishOldest_orSetCookie() throws Exception {
        // We check the path where the study has preview allowed and this is not the first call of startStudy.
        // We don't have to set StudyResult.StudyState.PRE because this state would be checked in
        // StudyAuthorisation::checkWorkerAllowedToStartStudy and this is mocked and does not throw an exception.
        Study study = newStudy(11L);
        Batch batch = newBatch(12L, study);
        GeneralSingleWorker worker = newGSWorker(13L);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        // Worker cookie present
        long wid1 = 13L;
        when(generalSingleCookieService.fetchWorkerIdByStudy(study)).thenReturn(wid1);
        when(publixUtils.retrieveWorker(wid1)).thenReturn(worker);

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
        verify(generalSingleCookieService, never()).set(any(), any());
        verify(idCookieService).writeIdCookie(existing);
        verify(publixUtils).setUrlQueryParameter(request, existing);
        verifyNoInteractions(resultCreator);
    }

    @Test
    public void startStudy_withWorkerCookie_missingIdCookie_finishesOldest_andSetsCookie() throws Exception {
        Study study = newStudy(21L);
        Batch batch = newBatch(22L, study);
        GeneralSingleWorker worker = newGSWorker(23L);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        long wid2 = 23L;
        when(generalSingleCookieService.fetchWorkerIdByStudy(study)).thenReturn(wid2);
        when(publixUtils.retrieveWorker(wid2)).thenReturn(worker);

        StudyResult existing = newStudyResult(30L, "sr-uuid-3", study, batch, worker);
        when(worker.getLastStudyResult()).thenReturn(Optional.of(existing));
        when(idCookieService.hasIdCookie(existing.getId())).thenReturn(false);
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(newComponent("comp-uuid-3"));

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue(loc.endsWith("/publix/sr-uuid-3/comp-uuid-3/start"));

        verify(publixUtils).finishOldestStudyResult();
        verify(generalSingleCookieService).set(study, worker);
        verify(idCookieService).writeIdCookie(existing);
        verifyNoInteractions(resultCreator);
    }

    @Test(expected = ForbiddenPublixException.class)
    public void startStudy_withWorkerCookie_butWorkerMissing_throws() throws Exception {
        Study study = newStudy(31L);
        Batch batch = newBatch(32L, study);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        Long unknownWorkerId = 999L;
        when(generalSingleCookieService.fetchWorkerIdByStudy(study)).thenReturn(unknownWorkerId);
        when(publixUtils.retrieveWorker(unknownWorkerId)).thenReturn(null);

        publix.startStudy(request, sl);
    }

    @Test(expected = ForbiddenPublixException.class)
    public void startStudy_withWorkerCookie_noLastStudyResult_throws() throws Exception {
        Study study = newStudy(41L);
        Batch batch = newBatch(42L, study);
        GeneralSingleWorker worker = newGSWorker(43L);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        long wid3 = 43L;
        when(generalSingleCookieService.fetchWorkerIdByStudy(study)).thenReturn(wid3);
        when(publixUtils.retrieveWorker(wid3)).thenReturn(worker);
        when(worker.getLastStudyResult()).thenReturn(Optional.empty());

        publix.startStudy(request, sl);
    }
}
