package services.publix.workers;

import controllers.publix.workers.PersonalMultiplePublix;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.PersonalMultipleWorker;
import org.fest.assertions.Fail;
import org.junit.Test;
import services.publix.PublixUtilsTest;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class PersonalMultiplePublixUtilsTest extends PublixUtilsTest<PersonalMultipleWorker> {

    @Inject
    private PersonalMultipleErrorMessages personalMultipleErrorMessages;

    @Inject
    private PersonalMultiplePublixUtils personalMultiplePublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {
        PersonalMultipleWorker worker = jpaApi.withTransaction(() -> {
            PersonalMultipleWorker w = new PersonalMultipleWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                PersonalMultipleWorker retrievedWorker =
                        personalMultiplePublixUtils.retrieveTypedWorker(worker.getId().toString());
                assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
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
                personalMultiplePublixUtils
                        .retrieveTypedWorker(generalSingleWorker.getId().toString());
                Fail.fail();
            } catch (PublixException e) {
                assertThat(e.getMessage()).isEqualTo(personalMultipleErrorMessages
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
        queryString.put(PersonalMultiplePublix.PERSONAL_MULTIPLE_WORKER_ID, new String[]{"123"});
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
