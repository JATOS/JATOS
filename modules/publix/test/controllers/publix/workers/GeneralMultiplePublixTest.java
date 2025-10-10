package controllers.publix.workers;

import controllers.publix.GeneralMultipleGroupChannel;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.PersonalMultipleWorker;
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
import services.publix.workers.GeneralMultipleStudyAuthorisation;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static play.test.Helpers.SEE_OTHER;
import static play.test.Helpers.fakeRequest;

/**
 * Unit tests for GeneralMultiplePublix methods (class-local logic only).
 */
public class GeneralMultiplePublixTest {

    private PublixUtils publixUtils;
    private GeneralMultipleStudyAuthorisation studyAuthorisation;
    private ResultCreator resultCreator;
    private WorkerCreator workerCreator;
    private GeneralMultipleGroupChannel groupChannel;
    private IdCookieService idCookieService;
    private StudyAssets studyAssets;
    private StudyLogger studyLogger;

    private GeneralMultiplePublix publix;

    private final JPAApi jpa = mock(JPAApi.class);

    @Before
    public void setUp() {
        publixUtils = mock(PublixUtils.class);
        studyAuthorisation = mock(GeneralMultipleStudyAuthorisation.class);
        resultCreator = mock(ResultCreator.class);
        workerCreator = mock(WorkerCreator.class);
        groupChannel = mock(GeneralMultipleGroupChannel.class);
        idCookieService = mock(IdCookieService.class);
        studyAssets = mock(StudyAssets.class);
        studyLogger = mock(StudyLogger.class);
        PublixErrorMessages errorMessages = mock(PublixErrorMessages.class);
        JsonUtils jsonUtils = mock(JsonUtils.class);
        ComponentResultDao componentResultDao = mock(ComponentResultDao.class);
        StudyResultDao studyResultDao = mock(StudyResultDao.class);
        IOUtils ioUtils = null; // not needed in this unit test

        publix = new GeneralMultiplePublix(jpa, publixUtils, studyAuthorisation, resultCreator, workerCreator,
                groupChannel, idCookieService, errorMessages, studyAssets, jsonUtils,
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

    private static StudyLink newStudyLink(Batch batch) {
        StudyLink sl = new StudyLink();
        sl.setBatch(batch);
        sl.setStudyCode("code-gm");
        return sl;
    }

    private static GeneralMultipleWorker newWorker(long id) {
        GeneralMultipleWorker w = mock(GeneralMultipleWorker.class);
        when(w.getId()).thenReturn(id);
        return w;
    }

    private static StudyResult newStudyResult(long id, String uuid, Study study, Batch batch, GeneralMultipleWorker worker) {
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
    public void startStudy_createsWorkerAndStudyResult_andRedirects() throws PublixException {
        Study study = newStudy(100L);
        Batch batch = newBatch(200L, study);
        StudyLink sl = newStudyLink(batch);
        Http.Request request = fakeRequest().build();

        GeneralMultipleWorker worker = newWorker(300L);
        when(workerCreator.createAndPersistGeneralMultipleWorker(batch)).thenReturn(worker);

        Component first = newComponent("comp-uuid-gm-1");
        when(publixUtils.retrieveFirstActiveComponent(study)).thenReturn(first);

        StudyResult sr = newStudyResult(400L, "sr-uuid-gm-1", study, batch, worker);
        when(resultCreator.createStudyResult(sl, worker)).thenReturn(sr);

        Result res = publix.startStudy(request, sl);

        assertEquals(SEE_OTHER, res.status());
        String loc = res.header("Location").orElse("");
        assertTrue("Redirect should go to startComponent of first component", loc.endsWith("/publix/sr-uuid-gm-1/comp-uuid-gm-1/start"));

        // Verify interactions specific to GeneralMultiplePublix
        verify(workerCreator).createAndPersistGeneralMultipleWorker(batch);
        verify(studyAuthorisation).checkWorkerAllowedToStartStudy(any(), eq(worker), eq(study), eq(batch));
        verify(publixUtils).finishOldestStudyResult();
        verify(resultCreator).createStudyResult(sl, worker);
        verify(publixUtils).setUrlQueryParameter(request, sr);
        verify(idCookieService).writeIdCookie(sr);
        verify(studyLogger).log(eq(sl), contains("Started study run with " + PersonalMultipleWorker.UI_WORKER_TYPE), eq(worker));
    }
}
