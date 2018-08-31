package services.publix.workers;

import controllers.publix.workers.GeneralSinglePublix;
import exceptions.publix.ForbiddenPublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.gui.UserService;
import services.publix.PublixUtilsTest;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests class GeneralSinglePublixUtils. Most test cases are in parent class
 * PublixUtilsTest.
 *
 * @author Kristian Lange
 */
public class GeneralSinglePublixUtilsTest extends PublixUtilsTest<GeneralSingleWorker> {

    @Inject
    private GeneralSingleErrorMessages generalSingleErrorMessages;

    @Inject
    private GeneralSinglePublixUtils generalSinglePublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {
        Worker worker = jpaApi.withTransaction(() -> {
            GeneralSingleWorker w = new GeneralSingleWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                GeneralSingleWorker retrievedWorker =
                        generalSinglePublixUtils.retrieveTypedWorker(worker.getId());
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
                generalSinglePublixUtils.retrieveTypedWorker(admin.getWorker().getId());
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(generalSingleErrorMessages
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
        queryString.put(GeneralSinglePublix.GENERALSINGLE, new String[]{"123"});
        queryString.put("batchId", new String[]{"3"});
        queryString.put("pre", new String[]{""});
        testHelper.mockContext(queryString);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.setUrlQueryParameter(studyResult);
            assertThat(studyResult.getUrlQueryParameters()).isEqualTo(
                    "{\"foo\":\"bar\",\"para2\":\"1234567890\",\"para3\":\"i%20like%20gizmodo\"}");
        });
    }

}
