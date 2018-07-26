package services.publix.workers;

import controllers.publix.workers.MTPublix;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.publix.PublixUtilsTest;

import javax.inject.Inject;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixUtilsTest<MTWorker> {

    @Inject
    private MTErrorMessages mtErrorMessages;

    @Inject
    private MTPublixUtils mtPublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {

        MTWorker mtWorker = jpaApi.withTransaction(() -> {
            MTWorker w = new MTWorker();
            workerDao.create(w);
            return w;
        });
        MTSandboxWorker mtSandboxWorker = jpaApi.withTransaction(() -> {
            MTSandboxWorker w = new MTSandboxWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                MTWorker retrievedWorker = mtPublixUtils
                        .retrieveTypedWorker(mtWorker.getId());
                assertThat(retrievedWorker.getId()).isEqualTo(mtWorker.getId());
                retrievedWorker = mtPublixUtils
                        .retrieveTypedWorker(mtSandboxWorker.getId());
                assertThat(retrievedWorker.getId())
                        .isEqualTo(mtSandboxWorker.getId());
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkRetrieveTypedWorkerWrongType() {
        GeneralSingleWorker generalSingleWorker = jpaApi.withTransaction(() -> {
            GeneralSingleWorker w = new GeneralSingleWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                mtPublixUtils.retrieveTypedWorker(generalSingleWorker.getId());
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(mtErrorMessages
                        .workerNotCorrectType(generalSingleWorker.getId()));
            }
        });
    }

    @Test
    public void checkSetUrlQueryParameter() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        long studyResultId = super.createStudyResult(study);

        // Put URL query parameters in the Context
        Map<String, String[]> queryString = new HashMap<>();
        queryString.put("foo", new String[]{"bar"});
        queryString.put("para2", new String[]{"1234567890"});
        queryString.put("para3", new String[]{"i%20like%20gizmodo"});
        queryString.put(MTPublix.MT_WORKER_ID, new String[]{"123"});
        queryString.put(MTPublix.ASSIGNMENT_ID, new String[]{"123"});
        queryString.put("hitId", new String[]{"4"});
        queryString.put("turkSubmitTo", new String[]{"sandbox"});
        queryString.put("batchId", new String[]{"3"});
        testHelper.mockContext(queryString);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.setUrlQueryParameter(studyResult);
            assertThat(studyResult.getUrlQueryParameters()).isEqualTo(
                    "{\"workerId\":\"123\",\"foo\":\"bar\",\"para2\":\"1234567890\",\"para3\":\"i%20like%20gizmodo\"}");
        });
    }

}
