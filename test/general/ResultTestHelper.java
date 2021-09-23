package general;

import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.UserDao;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import models.common.*;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;
import services.gui.UserService;
import services.publix.PublixUtils;
import services.publix.ResultCreator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kristian Lange
 */
@SuppressWarnings({ "deprecation", "OptionalGetWithoutIsPresent" })
@Singleton
public class ResultTestHelper {

    @Inject
    private JPAApi jpaApi;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private PublixUtils publixUtils;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyDao studyDao;

    @Inject
    private StudyLinkDao studyLinkDao;

    public List<Long> createTwoComponentResults(long studyId) {
        return jpaApi.withTransaction(() -> {
            try {
                Study study = studyDao.findById(studyId);
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                List<Long> idList = new ArrayList<>();
                StudyLink studyLink = fetchStudyLink(study.getDefaultBatch(), JatosWorker.WORKER_TYPE);

                StudyResult studyResult = resultCreator.createStudyResult(studyLink, admin.getWorker());
                ComponentResult componentResult1 = publixUtils.startComponent(study.getFirstComponent().get(),
                        studyResult);
                ComponentResult componentResult2 = publixUtils.startComponent(study.getFirstComponent().get(),
                        studyResult);

                idList.add(componentResult1.getId());
                idList.add(componentResult2.getId());

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
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                List<Long> idList = new ArrayList<>();
                StudyLink studyLink = fetchStudyLink(study.getDefaultBatch(), JatosWorker.WORKER_TYPE);

                StudyResult studyResult1 = resultCreator.createStudyResult(studyLink, admin.getWorker());
                ComponentResult componentResult11 =
                        publixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
                componentResult11.setData("First ComponentResult's data of the first StudyResult.");
                ComponentResult componentResult12 =
                        publixUtils.startComponent(study.getFirstComponent().get(), studyResult1);
                componentResult12.setData("Second ComponentResult's data of the first StudyResult.");

                StudyResult studyResult2 = resultCreator.createStudyResult(studyLink, admin.getWorker());
                ComponentResult componentResult21 = publixUtils
                        .startComponent(study.getFirstComponent().get(), studyResult2);
                componentResult21.setData("First ComponentResult's data of the second StudyResult.");
                ComponentResult componentResult22 = publixUtils
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

    public StudyResult createStudyResult(Study study) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            StudyLink studyLink = fetchStudyLink(study.getDefaultBatch(), JatosWorker.WORKER_TYPE);
            return resultCreator.createStudyResult(studyLink, admin.getWorker());
        });
    }

    public StudyResult createStudyResult(Batch batch, Worker worker, StudyResult.StudyState studyState) {
        return jpaApi.withTransaction(() -> {
            StudyLink studyLink = fetchStudyLink(batch, worker.getWorkerType());
            StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
            studyResult.setStudyState(studyState);
            return studyResult;
        });
    }

    public StudyLink fetchStudyLink(Batch batch, String workerType) {
        return studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, workerType)));
    }

}
