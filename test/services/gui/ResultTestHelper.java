package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

/**
 * @author Kristian Lange
 */
public class ResultTestHelper {

	@Inject
	private JPAApi jpaApi;

	@Inject
	private ResultCreator resultCreator;

	@Inject
	private ResultService resultService;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

	@Inject
	private UserDao userDao;

	@Inject
	private StudyDao studyDao;

	public String createTwoComponentResults(long studyId) {
		return jpaApi.withTransaction(() -> {
			try {
				Study study = studyDao.findById(studyId);
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

				StudyResult studyResult = resultCreator.createStudyResult(study,
						study.getDefaultBatch(), admin.getWorker());
				ComponentResult componentResult1 = jatosPublixUtils
						.startComponent(study.getFirstComponent(), studyResult);
				ComponentResult componentResult2 = jatosPublixUtils
						.startComponent(study.getFirstComponent(), studyResult);
				String ids = componentResult1.getId() + ", "
						+ componentResult2.getId();

				// Check that we have 2 results
				List<Long> idList = resultService.extractResultIds(ids);
				List<ComponentResult> componentResultList = resultService
						.getComponentResults(idList);
				assertThat(componentResultList.size()).isEqualTo(2);
				return ids;
			} catch (ForbiddenReloadException | BadRequestException
					| NotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public String createTwoStudyResults(long studyId) {
		return jpaApi.withTransaction(() -> {
			try {
				Study study = studyDao.findById(studyId);
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

				StudyResult studyResult1 = resultCreator.createStudyResult(
						study, study.getDefaultBatch(), admin.getWorker());
				ComponentResult componentResult11 = jatosPublixUtils
						.startComponent(study.getFirstComponent(),
								studyResult1);
				componentResult11.setData(
						"First ComponentResult's data of the first StudyResult.");
				ComponentResult componentResult12 = jatosPublixUtils
						.startComponent(study.getFirstComponent(),
								studyResult1);
				componentResult12.setData(
						"Second ComponentResult's data of the first StudyResult.");

				StudyResult studyResult2 = resultCreator.createStudyResult(
						study, study.getBatchList().get(0), admin.getWorker());
				ComponentResult componentResult21 = jatosPublixUtils
						.startComponent(study.getFirstComponent(),
								studyResult1);
				componentResult21.setData(
						"First ComponentResult's data of the second StudyResult.");
				ComponentResult componentResult22 = jatosPublixUtils
						.startComponent(study.getFirstComponent(),
								studyResult1);
				componentResult22.setData(
						"Second ComponentResult's data of the second StudyResult.");

				String ids = studyResult1.getId() + ", " + studyResult2.getId();

				// Check that we have 2 results
				List<Long> idList = resultService.extractResultIds(ids);
				List<StudyResult> studyResultList = resultService
						.getStudyResults(idList);
				assertThat(studyResultList.size()).isEqualTo(2);

				return ids;
			} catch (ForbiddenReloadException | BadRequestException
					| NotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

}
