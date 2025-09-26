package modules.gui.services;

import com.pivovarit.function.ThrowingFunction;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.ForbiddenException;
import models.common.*;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.gui.ResultRemover;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import testutils.JatosTest;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link ResultRemover}
 *
 * @author Kristian Lange
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class ResultRemoverTest extends JatosTest {

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

        // Remove both component results and expect the (now empty) StudyResult to be removed too
        jpaApi.withTransaction(unchecked((em) -> resultRemover.removeComponentResults(ids, admin, true)));

        // Verify: component results gone, study result gone
        jpaApi.withTransaction(unchecked((em) -> {
            assertThat(componentResultDao.findById(ids.get(0))).isNull();
            assertThat(componentResultDao.findById(ids.get(1))).isNull();
            assertThat(studyResultDao.findIdsByStudyId(studyId)).isEmpty();
        }));
    }


    @Test
    public void removeComponentResults_wrongUser_forbidden() {
        Long studyId = importExampleStudy();
        List<Long> ids = createTwoComponentResults(studyId);

        jpaApi.withTransaction(unchecked((em) -> {
            User testUser = createUser("foo@foo.org");
            try {
                resultRemover.removeComponentResults(ids, testUser, true);
                Fail.fail();
            } catch (ForbiddenException e) {
                // expected
            }
        }));
    }

    @Test
    public void checkRemoveComponents_resultNotFound() {
        Long studyId = importExampleStudy();
        List<Long> ids = createTwoComponentResults(studyId);

        // Now try to remove the results, but one of the result IDs doesn't exist
        jpaApi.withTransaction(unchecked((em) -> {
            ids.add(1111L); // add ID that doesn't exist
            resultRemover.removeComponentResults(ids, admin, false);
        }));

        // Verify: NO result is removed
        jpaApi.withTransaction(unchecked((em) -> {
            assertThat(componentResultDao.findById(ids.get(0))).isNull();
            assertThat(componentResultDao.findById(ids.get(1))).isNull();
            assertThat(studyResultDao.findIdsByStudyId(studyId)).isNotEmpty();
        }));
    }

    @Test
    public void removeStudyResults_shouldRemoveStudyResultsAndTheirComponents() {
        Long studyId = importExampleStudy();
        // Create two StudyResults with some ComponentResults each
        List<Long> studyResultIds = createTwoStudyResults(studyId);

        // Remove the StudyResults
        jpaApi.withTransaction(unchecked((em) -> resultRemover.removeStudyResults(studyResultIds, admin)));

        // Verify they are gone, and no ComponentResults remain for them
        jpaApi.withTransaction(unchecked((em) -> {
            for (Long srid : studyResultIds) {
                assertThat(studyResultDao.findById(srid)).isNull();
                assertThat(studyResultDao.findIdsByStudyId(studyId)).isEmpty();
            }
        }));
    }

    @Test
    public void removeStudyResults_wrongUser_forbidden() {
        Long studyId = importExampleStudy();
        List<Long> studyResultIds = createTwoStudyResults(studyId);

        jpaApi.withTransaction(unchecked((em) -> {
            User testUser = createUser("bar@bar.org");
            try {
                resultRemover.removeStudyResults(studyResultIds, testUser);
                Fail.fail();
            } catch (ForbiddenException e) {
                // expected
            }
        }));
    }

    public List<Long> createTwoComponentResults(long studyId) {
        return jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Worker adminWorker = workerDao.findById(admin.getWorker().getId());
            List<Long> crids = new ArrayList<>();
            StudyLink studyLink = fetchStudyLink(study.getDefaultBatch());

            StudyResult studyResult = resultCreator.createStudyResult(studyLink, adminWorker);
            ComponentResult cr1 = publixUtils.startComponent(study.getFirstComponent().get(), studyResult);
            ComponentResult cr2 = publixUtils.startComponent(study.getFirstComponent().get(), studyResult);

            crids.add(cr1.getId());
            crids.add(cr2.getId());
            return crids;
        }));
    }

    public List<Long> createTwoStudyResults(long studyId) {
        return jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Worker adminWorker = workerDao.findById(admin.getWorker().getId());
            List<Long> idList = new ArrayList<>();
            StudyLink studyLink = fetchStudyLink(study.getDefaultBatch());

            StudyResult studyResult1 = resultCreator.createStudyResult(studyLink, adminWorker);
            publixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
            publixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
            StudyResult studyResult2 = resultCreator.createStudyResult(studyLink, adminWorker);
            publixUtils.startComponent(study.getFirstComponent().get(), studyResult2);
            publixUtils.startComponent(study.getFirstComponent().get(), studyResult2);
            idList.add(studyResult1.getId());
            idList.add(studyResult2.getId());
            return idList;
        }));
    }

    private StudyLink fetchStudyLink(Batch batch) {
        return studyLinkDao.findFirstByBatchAndWorkerType(batch, JatosWorker.WORKER_TYPE)
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, JatosWorker.WORKER_TYPE)));
    }
}
