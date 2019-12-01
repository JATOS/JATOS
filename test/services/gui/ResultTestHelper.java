package services.gui;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.db.jpa.JPAApi;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kristian Lange
 */
@Singleton
public class ResultTestHelper {

    @Inject
    private JPAApi jpaApi;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private JatosPublixUtils jatosPublixUtils;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyDao studyDao;

    public List<Long> createTwoComponentResults(long studyId) {
        return jpaApi.withTransaction(() -> {
            try {
                Study study = studyDao.findById(studyId);
                User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
                List<Long> idList = new ArrayList<>();

                StudyResult studyResult = resultCreator.createStudyResult(study, study.getDefaultBatch(),
                        admin.getWorker());
                ComponentResult componentResult1 = jatosPublixUtils.startComponent(study.getFirstComponent().get(),
                        studyResult);
                ComponentResult componentResult2 = jatosPublixUtils.startComponent(study.getFirstComponent().get(),
                        studyResult);

                idList.add(componentResult1.getId());
                idList.add(componentResult2.getId());

                // Check that we have 2 results
                return idList;
            } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Long> createTwoStudyResults(long studyId) {
        return jpaApi.withTransaction(() -> {
            try {
                Study study = studyDao.findById(studyId);
                User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
                List<Long> idList = new ArrayList<>();

                StudyResult studyResult1 = resultCreator.createStudyResult(study, study.getDefaultBatch(),
                        admin.getWorker());
                ComponentResult componentResult11 =
                        jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
                componentResult11.setData("First ComponentResult's data of the first StudyResult.");
                ComponentResult componentResult12 =
                        jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
                componentResult12.setData("Second ComponentResult's data of the first StudyResult.");

                StudyResult studyResult2 = resultCreator
                        .createStudyResult(study, study.getBatchList().get(0), admin.getWorker());
                ComponentResult componentResult21 = jatosPublixUtils
                        .startComponent(study.getFirstComponent().get(), studyResult2);
                componentResult21.setData("First ComponentResult's data of the second StudyResult.");
                ComponentResult componentResult22 = jatosPublixUtils
                        .startComponent(study.getFirstComponent().get(), studyResult2);
                componentResult22.setData("Second ComponentResult's data of the second StudyResult.");

                idList.add(studyResult1.getId());
                idList.add(studyResult2.getId());

                return idList;
            } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
