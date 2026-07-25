package services.gui;

import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.common.ForbiddenException;
import http.common.Http.Context;
import models.common.*;
import models.common.workers.Worker;
import models.common.workers.WorkerType;
import org.assertj.core.api.Fail;
import org.junit.Test;
import play.test.Helpers;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import testutils.JatosTest;

import javax.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResultRemover}
 */
public class ResultRemoverIntegrationTest extends JatosTest {

    @Inject
    private ResultRemover resultRemover;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private PublixUtils publixUtils;

    @Inject
    private StudyDao studyDao;

    @Inject
    private WorkerDao workerDao;

    @Inject
    private StudyLinkDao studyLinkDao;

    @Inject
    private ComponentResultDao componentResultDao;

    @Inject
    private StudyResultDao studyResultDao;

    @Test
    public void checkRemoveComponentResults() {
        Long studyId = importExampleStudy();

        List<Long> ids = createTwoComponentResults(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Remove both component results and expect the (now empty) StudyResult to be removed too
        resultRemover.removeComponentResults(ids, true);

        // Verify: component results gone, study result gone
        assertThat(componentResultDao.findById(ids.get(0))).isNull();
        assertThat(componentResultDao.findById(ids.get(1))).isNull();
        assertThat(studyResultDao.findIdsByStudyId(studyId)).isEmpty();
    }

    @Test
    public void removeComponentResults_wrongUser_forbidden() {
        Long studyId = importExampleStudy();
        List<Long> ids = createTwoComponentResults(studyId);

        User testUser = createUser("foo@foo.org");
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, testUser);
        try {
            resultRemover.removeComponentResults(ids, true);
            Fail.fail();
        } catch (ForbiddenException e) {
            // expected
        }
    }

    @Test
    public void checkRemoveComponents_resultNotFound() {
        Long studyId = importExampleStudy();
        List<Long> ids = createTwoComponentResults(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Now try to remove the results, but one of the result IDs doesn't exist
        ids.add(1111L); // add ID that doesn't exist
        resultRemover.removeComponentResults(ids, false);

        // Verify: NO result is removed
        assertThat(componentResultDao.findById(ids.get(0))).isNull();
        assertThat(componentResultDao.findById(ids.get(1))).isNull();
        assertThat(studyResultDao.findIdsByStudyId(studyId)).isNotEmpty();
    }

    @Test
    public void removeStudyResults_shouldRemoveStudyResultsAndTheirComponents() {
        Long studyId = importExampleStudy();
        // Create two StudyResults with some ComponentResults each
        List<Long> studyResultIds = createTwoStudyResults(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Remove the StudyResults
        jpaApi.withTransaction((EntityManager em) -> resultRemover.removeStudyResults(studyResultIds));

        // Verify they are gone, and no ComponentResults remain for them
        for (Long srid : studyResultIds) {
            assertThat(studyResultDao.findById(srid)).isNull();
            assertThat(studyResultDao.findIdsByStudyId(studyId)).isEmpty();
        }
    }

    @Test
    public void removeStudyResults_wrongUser_forbidden() {
        Long studyId = importExampleStudy();
        List<Long> studyResultIds = createTwoStudyResults(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        User testUser = createUser("bar@bar.org");
        Context.current().args().put(SIGNEDIN_USER, testUser);

        try {
            resultRemover.removeStudyResults(studyResultIds);
            jpaApi.withTransaction((EntityManager em) -> resultRemover.removeStudyResults(studyResultIds));
            Fail.fail();
        } catch (ForbiddenException e) {
            // expected
        }
    }

    public List<Long> createTwoComponentResults(long studyId) {
        return jpaApi.withTransaction((EntityManager em) -> {
            Study study = studyDao.findById(studyId);
            Worker adminWorker = workerDao.findById(admin.getWorker().getId());
            List<Long> crids = new ArrayList<>();
            StudyLink studyLink = fetchStudyLink(study.getDefaultBatch());

            StudyResult studyResult = resultCreator.createStudyResult(studyLink, adminWorker);
            ComponentResult cr1 = publixUtils.startComponentRun(study.getFirstComponent().orElseThrow(), studyResult);
            ComponentResult cr2 = publixUtils.startComponentRun(study.getFirstComponent().orElseThrow(), studyResult);

            crids.add(cr1.getId());
            crids.add(cr2.getId());
            return crids;
        });
    }

    public List<Long> createTwoStudyResults(long studyId) {
        return jpaApi.withTransaction((EntityManager em) -> {
            Study study = studyDao.findById(studyId);
            Worker adminWorker = workerDao.findById(admin.getWorker().getId());
            List<Long> idList = new ArrayList<>();
            StudyLink studyLink = fetchStudyLink(study.getDefaultBatch());

            StudyResult studyResult1 = resultCreator.createStudyResult(studyLink, adminWorker);
            publixUtils.startComponentRun(study.getFirstComponent().orElseThrow(), studyResult1);
            publixUtils.startComponentRun(study.getFirstComponent().get(), studyResult1);
            StudyResult studyResult2 = resultCreator.createStudyResult(studyLink, adminWorker);
            publixUtils.startComponentRun(study.getFirstComponent().get(), studyResult2);
            publixUtils.startComponentRun(study.getFirstComponent().get(), studyResult2);
            idList.add(studyResult1.getId());
            idList.add(studyResult2.getId());
            return idList;
        });
    }

    private StudyLink fetchStudyLink(Batch batch) {
        return studyLinkDao.findFirstByBatchAndWorkerType(batch, WorkerType.JATOS)
                .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, WorkerType.JATOS)));
    }
}
