package services.publix.idcookie;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.UserDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import general.TestHelper;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.JatosWorker;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.Cookie;
import services.gui.UserService;
import services.publix.ResultCreator;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange (2017)
 */
public class IdCookieServiceTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private IdCookieService idCookieService;

    @Inject
    private IdCookieTestHelper idCookieTestHelper;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private UserDao userDao;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();
        testHelper.removeStudyAssetsRootDir();
    }

    /**
     * IdCookieService.getIdCookie(): Check normal functioning - it should
     * return the IdCookies specified by the study result ID
     */
    @Test
    public void checkGetIdCookie() throws BadRequestPublixException,
            InternalServerErrorPublixException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1L);
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        testHelper.mockContext(cookieList);

        // Get IdCookie for study result ID 1l
        IdCookieModel idCookie = idCookieService.getIdCookie(1L);
        assertThat(idCookie).isNotNull();
        assertThat(idCookie.getStudyResultId()).isEqualTo(1L);

        // Get IdCookie for study result ID 1l
        idCookie = idCookieService.getIdCookie(2L);
        assertThat(idCookie).isNotNull();
        assertThat(idCookie.getStudyResultId()).isEqualTo(2L);
    }

    /**
     * IdCookieService.getIdCookie(): if an IdCookie for the given study result
     * ID doesn't exist an BadRequestPublixException should be thrown
     */
    @Test
    public void checkGetIdCookieNotFound() throws InternalServerErrorPublixException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        testHelper.mockContext(cookieList);

        try {
            idCookieService.getIdCookie(2L);
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
    public void checkOneIdCookieHasThisStudyAssets() throws InternalServerErrorPublixException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1L);
        idCookie1.setStudyAssets("test_study_assets1");
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2L);
        idCookie2.setStudyAssets("test_study_assets2");
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        testHelper.mockContext(cookieList);

        assertThat(idCookieService.oneIdCookieHasThisStudyAssets("test_study_assets1")).isTrue();
        assertThat(idCookieService.oneIdCookieHasThisStudyAssets("test_study_assets2")).isTrue();
        assertThat(idCookieService.oneIdCookieHasThisStudyAssets("NOT_study_assets")).isFalse();
    }

    /**
     * IdCookieService.getIdCookie(): in case there are no cookies it should
     * return false
     */
    @Test
    public void checkOneIdCookieHasThisStudyAssetsEmptyList()
            throws InternalServerErrorPublixException {
        List<Cookie> cookieList = new ArrayList<>();
        testHelper.mockContext(cookieList);

        assertThat(idCookieService.oneIdCookieHasThisStudyAssets("test_study_assets")).isFalse();
    }

    /**
     * IdCookieService.writeIdCookie(): Check for proper IdCookie name and
     * values.
     */
    @Test
    public void checkWriteIdCookie()
            throws InternalServerErrorPublixException, BadRequestPublixException {
        List<Cookie> cookieList = new ArrayList<>();
        testHelper.mockContext(cookieList);

        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        StudyResult studyResult = createAndPersistStudyResult(study);

        User admin = testHelper.getAdmin();
        idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(), studyResult);

        IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
        assertThat(idCookie).isNotNull();
        // Check naming
        assertThat(idCookie.getName()).startsWith(IdCookieModel.ID_COOKIE_NAME + "_");
        // Check proper ID cookie values
        assertThat(idCookie.getBatchId()).isEqualTo(studyResult.getBatch().getId());
        assertThat(idCookie.getComponentId()).isNull();
        assertThat(idCookie.getComponentPosition()).isNull();
        assertThat(idCookie.getComponentResultId()).isNull();
        assertThat(idCookie.getCreationTime()).isGreaterThan(0L);
        assertThat(idCookie.getGroupResultId()).isNull();
        assertThat(idCookie.getIndex()).isEqualTo(0);
        assertThat(idCookie.getJatosRun()).isNull();
        assertThat(idCookie.getName()).isEqualTo("JATOS_IDS_0");
        assertThat(idCookie.getStudyAssets()).isEqualTo("basic_example_study");
        assertThat(idCookie.getStudyId()).isEqualTo(study.getId());
        assertThat(idCookie.getStudyResultId()).isEqualTo(studyResult.getId());
        assertThat(idCookie.getWorkerId()).isEqualTo(admin.getWorker().getId());
        assertThat(idCookie.getWorkerType()).isEqualTo(JatosWorker.WORKER_TYPE);
        assertThat(idCookie.getUrlBasePath()).isEqualTo("/somepath/");
    }

    /**
     * IdCookieService.writeIdCookie(): If the new IdCookie has the same ID as
     * an existing one it should be overwritten. Additionally check for proper
     * IdCookie name and values.
     */
    @Test
    public void checkWriteIdCookieOverwriteWithSameId()
            throws InternalServerErrorPublixException, BadRequestPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        StudyResult studyResult = createAndPersistStudyResult(study);

        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(studyResult.getId());
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2222L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        testHelper.mockContext(cookieList);

        User admin = testHelper.getAdmin();
        idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(), studyResult);

        // Check that the old IdCookie for the study result ID 1l is overwritten
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
        assertThat(idCookie).isNotNull();
        assertThat(idCookieService.getIdCookieCollection().size()).isEqualTo(2);
        assertThat(idCookie.getStudyAssets()).isEqualTo("basic_example_study");
    }

    /**
     * IdCookieService.writeIdCookie(): If none of the existing IdCookies has
     * the ID of the new one and the max cookie number is not yet reached log
     * a new IdCookie.
     */
    @Test
    public void checkWriteIdCookieWriteNewCookie()
            throws InternalServerErrorPublixException, BadRequestPublixException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1111L);
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2222L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        testHelper.mockContext(cookieList);

        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        StudyResult studyResult = createAndPersistStudyResult(study);

        User admin = testHelper.getAdmin();
        idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(), studyResult);

        // Check that a new IdCookie is written
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
        assertThat(idCookie).isNotNull();
        assertThat(idCookieService.getIdCookieCollection().size()).isEqualTo(3);
    }

    /**
     * IdCookieService.writeIdCookie(): If none of the existing IdCookies has
     * the ID of the new one and the max cookie number is already reached an
     * InternalServerErrorPublixException should be thrown.
     */
    @Test
    public void checkWriteIdCookieOverwriteOldest() {
        List<Cookie> cookieList = new ArrayList<>();
        // Create max IdCookies with study result IDs starting from 100l
        for (long i = 100L; i < (100L
                + IdCookieCollection.MAX_ID_COOKIES); i++) {
            IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
            cookieList.add(idCookieTestHelper.buildCookie(idCookie));
        }
        testHelper.mockContext(cookieList);

        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        StudyResult studyResult = createAndPersistStudyResult(study);

        try {
            User admin = testHelper.getAdmin();
            idCookieService.writeIdCookie(admin.getWorker(), studyResult.getBatch(), studyResult);
            Fail.fail();
        } catch (InternalServerErrorPublixException e) {
            // check throwing is enough
        }
    }

    /**
     * IdCookieService.discardIdCookie(): just check removal of the IdCookie
     * (this method is just a wrapper and the actual method is tested elsewhere)
     */
    @Test
    public void checkDiscardIdCookie() throws InternalServerErrorPublixException {
        IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(1L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie));
        testHelper.mockContext(cookieList);

        idCookieService.discardIdCookie(1L);

        // Check that IdCookie is gone
        try {
            idCookieService.getIdCookie(1L);
            Fail.fail();
        } catch (BadRequestPublixException e) {
            // check throwing is enough
        }
    }

    /**
     * IdCookieService.maxIdCookiesReached(): max reached
     */
    @Test
    public void checkMaxIdCookiesReachedMaxReached() throws InternalServerErrorPublixException {
        // Create max IdCookies
        List<Cookie> cookieList = new ArrayList<>();
        for (long i = 1l; i < (IdCookieCollection.MAX_ID_COOKIES + 1); i++) {
            IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
            cookieList.add(idCookieTestHelper.buildCookie(idCookie));
        }
        testHelper.mockContext(cookieList);

        assertThat(idCookieService.maxIdCookiesReached()).isTrue();
    }

    /**
     * IdCookieService.maxIdCookiesReached(): not reached
     */
    @Test
    public void checkMaxIdCookiesReachedMaxNotReached() throws InternalServerErrorPublixException {
        // Just create one (< max ID cookies)
        IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(1L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie));
        testHelper.mockContext(cookieList);

        assertThat(idCookieService.maxIdCookiesReached()).isFalse();
    }

    /**
     * IdCookieService.getOldestIdCookie(): check that the oldest IdCookie is
     * retrieved
     */
    @Test
    public void checkGetOldestIdCookie() throws InternalServerErrorPublixException {
        // Create a couple of IdCookie: the oldest will be the one that was
        // first added to the list
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1L);
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2L);
        IdCookieModel idCookie3 = idCookieTestHelper.buildDummyIdCookie(3L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie3));
        testHelper.mockContext(cookieList);

        IdCookieModel retrievedIdCookie = idCookieService.getOldestIdCookie();
        assertThat(retrievedIdCookie).isEqualTo(idCookie1);
    }

    /**
     * IdCookieService.getOldestIdCookie(): if there is no IdCookie it should
     * return null
     */
    @Test
    public void checkGetOldestIdCookieEmpty() throws InternalServerErrorPublixException {
        List<Cookie> cookieList = new ArrayList<>();
        testHelper.mockContext(cookieList);

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
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1L);
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2L);
        IdCookieModel idCookie3 = idCookieTestHelper.buildDummyIdCookie(3L);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie3));
        testHelper.mockContext(cookieList);

        Long studyResultId = idCookieService.getStudyResultIdFromOldestIdCookie();
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
        testHelper.mockContext(cookieList);

        Long studyResultId = idCookieService.getStudyResultIdFromOldestIdCookie();
        assertThat(studyResultId).isNull();
    }

    private StudyResult createAndPersistStudyResult(Study study) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            return resultCreator.createStudyResult(study, study.getDefaultBatch(), admin.getWorker());
        });
    }

}
