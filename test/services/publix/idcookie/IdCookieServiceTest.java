package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import general.AbstractTest;
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
		IdCookie idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		IdCookie idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
		cookieList.add(idCookieTestHelper.buildCookie(idCookie2));
		mockContext(cookieList);

		// Get IdCookie for study result ID 1l
		IdCookie idCookie = idCookieService.getIdCookie(1l);
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
		IdCookie idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
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
		IdCookie idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setStudyAssets("test_study_assets1");
		IdCookie idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
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

}
