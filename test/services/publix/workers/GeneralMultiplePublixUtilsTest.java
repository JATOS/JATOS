package services.publix.workers;

import controllers.publix.workers.GeneralMultiplePublix;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.GeneralMultipleWorker;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.gui.UserService;
import services.publix.PublixUtilsTest;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class GeneralMultiplePublixUtilsTest extends PublixUtilsTest<GeneralMultipleWorker> {

    @Inject
    private GeneralMultipleErrorMessages generalMultipleErrorMessages;

    @Inject
    private GeneralMultiplePublixUtils generalMultiplePublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {
        GeneralMultipleWorker worker = jpaApi.withTransaction(() -> {
            GeneralMultipleWorker w = new GeneralMultipleWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                GeneralMultipleWorker retrievedWorker = generalMultiplePublixUtils
                        .retrieveTypedWorker(worker.getId());
                assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkRetrieveTypedWorkerWrongType() {
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                generalMultiplePublixUtils.retrieveTypedWorker(admin.getWorker().getId());
                Fail.fail();
            } catch (PublixException e) {
                assertThat(e.getMessage()).isEqualTo(generalMultipleErrorMessages
                        .workerNotCorrectType(admin.getWorker().getId()));
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
        queryString.put(GeneralMultiplePublix.GENERALMULTIPLE, new String[]{"abc"});
        queryString.put("batchId", new String[]{"3"});
        testHelper.mockContext(queryString);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.setUrlQueryParameter(studyResult);
            assertThat(studyResult.getUrlQueryParameters()).isEqualTo(
                    "{\"foo\":\"bar\",\"para2\":\"1234567890\",\"para3\":\"i%20like%20gizmodo\"}");
        });
    }

}
