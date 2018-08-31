package services.publix.workers;

import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import org.fest.assertions.Fail;
import org.junit.Test;
import play.mvc.Http;
import services.gui.UserService;
import services.publix.PublixUtilsTest;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class JatosPublixUtilsTest extends PublixUtilsTest<JatosWorker> {

    @Inject
    private JatosErrorMessages jatosErrorMessages;

    @Inject
    private JatosPublixUtils jatosPublixUtils;

    @Test
    public void checkRetrieveTypedWorker() {
        JatosWorker retrievedWorker = jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                return jatosPublixUtils
                        .retrieveTypedWorker(admin.getWorker().getId());
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            assertThat(retrievedWorker.getId())
                    .isEqualTo(admin.getWorker().getId());
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
                jatosPublixUtils.retrieveTypedWorker(generalSingleWorker.getId());
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(jatosErrorMessages
                        .workerNotCorrectType(generalSingleWorker.getId()));
            }
        });
    }

    @Test
    public void checkRetrieveJatosRunFromSession()
            throws ForbiddenPublixException, BadRequestPublixException {
        testHelper.mockContext();
        Http.Context.current().session().put(JatosPublix.SESSION_JATOS_RUN,
                JatosRun.RUN_STUDY.name());

        JatosRun jatosRun = jatosPublixUtils.retrieveJatosRunFromSession();

        assertThat(jatosRun).isEqualTo(JatosRun.RUN_STUDY);
    }

    @Test
    public void checkRetrieveJatosRunFromSessionFail() {
        testHelper.mockContext();

        try {
            jatosPublixUtils.retrieveJatosRunFromSession();
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(
                    JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
        }
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
        queryString.put(JatosPublix.JATOS_WORKER_ID, new String[]{"123"});
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
