package services.publix.workers;

import controllers.publix.workers.PersonalSinglePublix;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.PersonalSingleWorker;
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
public class PersonalSinglePublixUtilsTest extends PublixUtilsTest<PersonalSingleWorker> {

    @Inject
    private PersonalSingleErrorMessages personalSingleErrorMessages;

    @Inject
    private PersonalSinglePublixUtils personalSinglePublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {
        PersonalSingleWorker worker = jpaApi.withTransaction(() -> {
            PersonalSingleWorker w = new PersonalSingleWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                PersonalSingleWorker retrievedWorker =
                        personalSinglePublixUtils.retrieveTypedWorker(worker.getId().toString());
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
                personalSinglePublixUtils
                        .retrieveTypedWorker(generalSingleWorker.getId().toString());
                Fail.fail();
            } catch (PublixException e) {
                assertThat(e.getMessage()).isEqualTo(personalSingleErrorMessages
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
        queryString.put(PersonalSinglePublix.PERSONAL_SINGLE_WORKER_ID, new String[]{"123"});
        queryString.put("batchId", new String[]{"3"});
        queryString.put("pre", new String[]{""});
        testHelper.mockContext(queryString);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.setUrlQueryParameter(studyResult);
            assertThat(studyResult.getUrlQueryParameters()).isEqualTo(
                    "{\"pre\":\"\",\"foo\":\"bar\",\"para2\":\"1234567890\",\"para3\":\"i%20like%20gizmodo\","
                            + "\"personalSingleWorkerId\":\"123\",\"batchId\":\"3\"}");
        });
    }

}
