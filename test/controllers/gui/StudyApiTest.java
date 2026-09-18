package controllers.gui;

import com.pivovarit.function.ThrowingFunction;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyLink;
import models.common.StudyResult;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import testutils.JatosTest;

import javax.inject.Inject;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.DELETE;
import static play.test.Helpers.OK;
import static play.test.Helpers.route;

/**
 * Integration tests for the study endpoints in {@link Api}.
 */
public class StudyApiTest extends JatosTest {

    @Inject
    private ComponentResultDao componentResultDao;

    @Inject
    private PublixUtils publixUtils;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private StudyDao studyDao;

    @Inject
    private StudyLinkDao studyLinkDao;

    @Inject
    private StudyResultDao studyResultDao;

    @Inject
    private WorkerDao workerDao;

    @Test
    public void deleteStudy_removesStudyAndResults() {
        Study study = importAndGetExampleStudy();
        Study otherStudy = importAndGetExampleStudy();
        Long studyId = study.getId();

        Long[] resultIds = jpaApi.withTransaction(ThrowingFunction.unchecked(em -> {
            Study managedStudy = studyDao.findById(studyId);
            Study managedOtherStudy = studyDao.findById(otherStudy.getId());
            Worker adminWorker = workerDao.findById(admin.getWorker().getId());
            StudyLink studyLink = studyLinkDao
                    .findFirstByBatchAndWorkerType(managedStudy.getDefaultBatch(), JatosWorker.WORKER_TYPE)
                    .orElseGet(() -> studyLinkDao.create(
                            new StudyLink(managedStudy.getDefaultBatch(), JatosWorker.WORKER_TYPE)));
            StudyResult studyResult = resultCreator.createStudyResult(studyLink, adminWorker);
            ComponentResult componentResult = publixUtils.startComponent(
                    managedStudy.getFirstComponent().get(), studyResult);

            // Simulate inconsistent legacy data: the StudyResult belongs to this study but references another
            // study's batch. Batch-based cleanup alone does not find this result.
            studyResult.setBatch(managedOtherStudy.getDefaultBatch());
            studyResultDao.update(studyResult);
            return new Long[]{studyResult.getId(), componentResult.getId()};
        }));

        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(DELETE)
                .header("Authorization", "Bearer " + apiToken)
                .uri("/jatos/api/v1/studies/" + studyId);

        Result response = route(application, request);

        assertThat(response.status()).isEqualTo(OK);
        jpaApi.withTransaction(em -> {
            assertThat(studyDao.findById(studyId)).isNull();
            assertThat(studyResultDao.findById(resultIds[0])).isNull();
            assertThat(studyResultDao.findIdsByStudyId(studyId)).isEmpty();
            assertThat(componentResultDao.findById(resultIds[1])).isNull();
        });
    }
}
