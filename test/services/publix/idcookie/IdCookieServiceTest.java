package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import general.AbstractTest;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.JatosWorker;
import play.mvc.Http.Cookie;

/**
 * @author Kristian Lange (2017)
 */
public class IdCookieServiceTest extends AbstractTest {

	private IdCookieService idCookieService;
	private IdCookieTestHelper idCookieTestHelper;

	@Override
	public void before() throws Exception {
		this.idCookieService = application.injector()
				.instanceOf(IdCookieService.class);
		this.idCookieTestHelper = application.injector()
				.instanceOf(IdCookieTestHelper.class);
	}

	@Override
	public void after() throws Exception {
	}

	/**
	 * IdCookieService.getIdCookie(): Check normal functioning - it should
	 * return the IdCookies specified by the study result ID
	 */
	@Test
	public void checkGetIdCookie() throws BadRequestPublixException,
			InternalServerErrorPublixException {
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		mockContext(cookieList);

		// Get IdCookie for study result ID 1l
		IdCookieModel idCookie = idCookieService.getIdCookie(1l);
		assertThat(idCookie).isNotNull();
		assertThat(idCookie.getStudyResultId()).isEqualTo(1l);

		// Get IdCookie for study result ID 1l
		idCookie = idCookieService.getIdCookie(2l);
		assertThat(idCookie).isNotNull();
		assertThat(idCookie.getStudyResultId()).isEqualTo(2l);
	}

	/**
	 * IdCookieService.getIdCookie(): if an IdCookie for the given study result
	 * ID doesn't exist an BadRequestPublixException should be thrown
	 */
	@Test
	public void checkGetIdCookieNotFound()
			throws InternalServerErrorPublixException {
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		mockContext(cookieList);

		try {
			idCookieService.getIdCookie(2l);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			// check throwing is enough
		}
	}

	/**
	 * IdCookieService.getIdCookie(): it should return true if at least one
	 * IdCookie has study assets that equal the given one and false otherwise
	 */
	@Test
	public void checkOneIdCookieHasThisStudyAssets()
			throws InternalServerErrorPublixException {
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setStudyAssets("test_study_assets1");
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
		idCookie2.setStudyAssets("test_study_assets2");
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		mockContext(cookieList);

		assertThat(idCookieService
				.oneIdCookieHasThisStudyAssets("test_study_assets1")).isTrue();
		assertThat(idCookieService
				.oneIdCookieHasThisStudyAssets("test_study_assets2")).isTrue();
		assertThat(idCookieService
				.oneIdCookieHasThisStudyAssets("NOT_study_assets")).isFalse();
	}

	/**
	 * IdCookieService.getIdCookie(): in case there are no cookies it should
	 * return false
	 */
	@Test
	public void checkOneIdCookieHasThisStudyAssetsEmptyList()
			throws InternalServerErrorPublixException {
		List<Cookie> cookieList = new ArrayList<>();
		mockContext(cookieList);

		assertThat(idCookieService
				.oneIdCookieHasThisStudyAssets("test_study_assets")).isFalse();
	}

	/**
	 * IdCookieService.writeIdCookie(): Check for proper IdCookie name and
	 * values.
	 */
	@Test
	public void checkWriteIdCookie() throws IOException,
			InternalServerErrorPublixException, BadRequestPublixException {
		List<Cookie> cookieList = new ArrayList<>();
		mockContext(cookieList);

		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(),
				studyResult);

		IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
		assertThat(idCookie).isNotNull();
		// Check naming
		assertThat(idCookie.getName())
				.startsWith(IdCookieModel.ID_COOKIE_NAME + "_");
		// Check proper ID cookie values
		assertThat(idCookie.getBatchId()).isEqualTo(1l);
		assertThat(idCookie.getComponentId()).isNull();
		assertThat(idCookie.getComponentPosition()).isNull();
		assertThat(idCookie.getComponentResultId()).isNull();
		assertThat(idCookie.getCreationTime()).isGreaterThan(0l);
		assertThat(idCookie.getGroupResultId()).isNull();
		assertThat(idCookie.getIndex()).isEqualTo(0);
		assertThat(idCookie.getJatosRun()).isNull();
		assertThat(idCookie.getName()).isEqualTo("JATOS_IDS_0");
		assertThat(idCookie.getStudyAssets()).isEqualTo("basic_example_study");
		assertThat(idCookie.getStudyId()).isEqualTo(1l);
		assertThat(idCookie.getStudyResultId()).isEqualTo(1l);
		assertThat(idCookie.getWorkerId()).isEqualTo(1l);
		assertThat(idCookie.getWorkerType()).isEqualTo(JatosWorker.WORKER_TYPE);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * IdCookieService.writeIdCookie(): If the new IdCookie has the same ID as
	 * an existing one it should be overwritten. Additionally check for proper
	 * IdCookie name and values.
	 */
	@Test
	public void checkWriteIdCookieOverwriteWithSameId() throws IOException,
			InternalServerErrorPublixException, BadRequestPublixException {
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2222l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		mockContext(cookieList);

		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(),
				studyResult);

		// Check that the old IdCookie for the study result ID 1l is overwritten
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
		assertThat(idCookie).isNotNull();
		assertThat(idCookieService.getIdCookieCollection().size()).isEqualTo(2);
		assertThat(idCookie.getStudyAssets()).isEqualTo("basic_example_study");

		// Clean-up
		removeStudy(study);
	}

	/**
	 * IdCookieService.writeIdCookie(): If none of the existing IdCookies has
	 * the ID of the new one and the max cookie number is not yet reached write
	 * a new IdCookie.
	 */
	@Test
	public void checkWriteIdCookieWriteNewCookie() throws IOException,
			InternalServerErrorPublixException, BadRequestPublixException {
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1111l);
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2222l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		mockContext(cookieList);

		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(),
				studyResult);

		// Check that a new IdCookie is written
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
		assertThat(idCookie).isNotNull();
		assertThat(idCookieService.getIdCookieCollection().size()).isEqualTo(3);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * IdCookieService.writeIdCookie(): If none of the existing IdCookies has
	 * the ID of the new one and the max cookie number is already reached an
	 * InternalServerErrorPublixException should be thrown.
	 */
	@Test
	public void checkWriteIdCookieOverwriteOldest()
			throws IOException, BadRequestPublixException {
		List<Cookie> cookieList = new ArrayList<>();
		// Create max IdCookies with study result IDs starting from 100l
		for (long i = 100l; i < (100l
				+ IdCookieCollection.MAX_ID_COOKIES); i++) {
			IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
			cookieList.add(idCookieTestHelper.buildCookie(idCookie));
		}
		mockContext(cookieList);

		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		try {
			idCookieService.writeIdCookie(admin.getWorker(),
					studyResult.getBatch(), studyResult);
			Fail.fail();
		} catch (InternalServerErrorPublixException e) {
			// check throwing is enough
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * IdCookieService.discardIdCookie(): just check removal of the IdCookie
	 * (this method is just a wrapper and the actual method is tested elsewhere)
	 */
	@Test
	public void checkDiscardIdCookie() throws BadRequestPublixException,
			InternalServerErrorPublixException {
		IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(1l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie));
		mockContext(cookieList);

		idCookieService.discardIdCookie(1l);

		// Check that IdCookie is gone
		try {
			idCookieService.getIdCookie(1l);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			// check throwing is enough
		}
	}

	/**
	 * IdCookieService.maxIdCookiesReached(): max reached
	 */
	@Test
	public void checkMaxIdCookiesReachedMaxReached()
			throws InternalServerErrorPublixException {
		// Create max IdCookies
		List<Cookie> cookieList = new ArrayList<>();
		for (long i = 1l; i < (IdCookieCollection.MAX_ID_COOKIES + 1); i++) {
			IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
			cookieList.add(idCookieTestHelper.buildCookie(idCookie));
		}
		mockContext(cookieList);

		assertThat(idCookieService.maxIdCookiesReached()).isTrue();
	}

	/**
	 * IdCookieService.maxIdCookiesReached(): not reached
	 */
	@Test
	public void checkMaxIdCookiesReachedMaxNotReached()
			throws InternalServerErrorPublixException {
		// Just create one (< max ID cookies)
		IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(1l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie));
		mockContext(cookieList);

		assertThat(idCookieService.maxIdCookiesReached()).isFalse();
	}

	/**
	 * IdCookieService.getOldestIdCookie(): check that the oldest IdCookie is
	 * retrieved
	 */
	@Test
	public void checkGetOldestIdCookie()
			throws InternalServerErrorPublixException {
		// Create a couple of IdCookie: the oldest will be the one that was
		// first added to the list
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
		IdCookieModel idCookie3 = idCookieTestHelper.buildDummyIdCookie(3l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie3));
		mockContext(cookieList);

		IdCookieModel retrievedIdCookie = idCookieService.getOldestIdCookie();
		assertThat(retrievedIdCookie).isEqualTo(idCookie1);
	}

	/**
	 * IdCookieService.getOldestIdCookie(): if there is no IdCookie it should
	 * return null
	 */
	@Test
	public void checkGetOldestIdCookieEmpty()
			throws InternalServerErrorPublixException {
		List<Cookie> cookieList = new ArrayList<>();
		mockContext(cookieList);

		IdCookieModel retrievedIdCookie = idCookieService.getOldestIdCookie();
		assertThat(retrievedIdCookie).isNull();
	}

	/**
	 * IdCookieService.getStudyResultIdFromOldestIdCookie(): return study result
	 * Id
	 */
	@Test
	public void checkGetStudyResultIdFromOldestIdCookie()
			throws InternalServerErrorPublixException {
		// Create a couple of IdCookie: the oldest will be the one that was
		// first added to the list
		IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
		IdCookieModel idCookie3 = idCookieTestHelper.buildDummyIdCookie(3l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie3));
		mockContext(cookieList);

		Long studyResultId = idCookieService
				.getStudyResultIdFromOldestIdCookie();
		assertThat(studyResultId).isEqualTo(1l);
	}

	/**
	 * IdCookieService.getStudyResultIdFromOldestIdCookie(): if there is no
	 * IdCookie it should return null
	 */
	@Test
	public void checkGetStudyResultIdFromOldestIdCookieEmpty()
			throws InternalServerErrorPublixException {
		List<Cookie> cookieList = new ArrayList<>();
		mockContext(cookieList);

		Long studyResultId = idCookieService
				.getStudyResultIdFromOldestIdCookie();
		assertThat(studyResultId).isNull();
	}

}
