package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import controllers.publix.workers.JatosPublix.JatosRun;
import general.AbstractTest;
import general.common.RequestScope;
import models.common.workers.JatosWorker;
import play.mvc.Http.Cookie;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;

/**
 * @author Kristian Lange
 */
public class IdCookieAccessorTest extends AbstractTest {

	private IdCookieAccessor idCookieAccessor;

	@Override
	public void before() throws Exception {
		idCookieAccessor = application.injector()
				.instanceOf(IdCookieAccessor.class);
	}

	@Override
	public void after() throws Exception {
	}

	public Cookie buildCookie(IdCookie idCookie) {
		String cookieValue = idCookieAccessor.asCookieString(idCookie);
		Cookie cookie = new Cookie(idCookie.getName(), cookieValue,
				Integer.MAX_VALUE, "/", "", false, false);
		return cookie;
	}

	@Test
	public void checkExtractSize() throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		IdCookie idCookie2 = IdCookieTestHelper.buildDummyIdCookie(2l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));
		cookieList.add(buildCookie(idCookie2));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(2);
		assertThat(idCookieCollection.findWithStudyResultId(1l))
				.isEqualTo(idCookie1);
		assertThat(idCookieCollection.findWithStudyResultId(2l))
				.isEqualTo(idCookie2);
	}

	@Test
	public void checkExtractEmpty() throws IdCookieAlreadyExistsException {
		List<Cookie> cookieList = new ArrayList<>();

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkExtractCheckValues()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(1);
		IdCookie idCookie1Extracted = idCookieCollection
				.findWithStudyResultId(1l);
		checkDefaultIdCookie(idCookie1Extracted);
		assertThat(RequestScope.has(IdCookieCollection.class.getSimpleName()))
				.isTrue();
	}

	private void checkDefaultIdCookie(IdCookie idCookie1Extracted) {
		assertThat(idCookie1Extracted.getBatchId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getComponentId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getComponentPosition()).isEqualTo(1);
		assertThat(idCookie1Extracted.getComponentResultId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getCreationTime()).isGreaterThan(0l);
		assertThat(idCookie1Extracted.getGroupResultId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getIndex()).isEqualTo(0);
		assertThat(idCookie1Extracted.getJatosRun())
				.isEqualTo(JatosRun.RUN_STUDY);
		assertThat(idCookie1Extracted.getName()).isEqualTo("JATOS_IDS_0");
		assertThat(idCookie1Extracted.getStudyAssets())
				.isEqualTo("test_study_assets");
		assertThat(idCookie1Extracted.getStudyId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getStudyResultId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getWorkerId()).isEqualTo(1l);
		assertThat(idCookie1Extracted.getWorkerType())
				.isEqualTo(JatosWorker.WORKER_TYPE);
	}

	@Test
	public void checkExtractMalformedIndex()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setName("JATOS_IDS"); // No index
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		// Since cookie is malformed it should be removed
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkExtractMalformedIdStrict()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setBatchId(null);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		// Since cookie is malformed it should be removed
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkExtractMalformedIdNotStrict()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setComponentId(null); // Not necessary
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		// Component ID is not necessary
		assertThat(idCookieCollection.size()).isEqualTo(1);
	}

	@Test
	public void checkExtractMalformedName()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setName("FOO_0"); // Wrong name but with index
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		// Since cookie is malformed it should be removed
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkExtractMalformedStudyAssets()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setStudyAssets(""); // Malformed study assets
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		// Since cookie is malformed it should be removed
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkExtractJatosRun() throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		idCookie1.setJatosRun(null); // JatosRun can be null
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(1);
	}

	@Test
	public void checkExtractFromRequestScope()
			throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		idCookieCollection.add(idCookie1);

		RequestScope.put(IdCookieCollection.class.getSimpleName(),
				idCookieCollection);

		// Extract from RequestScope instead of Request object
		idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(1);
		IdCookie idCookie1Extracted = idCookieCollection
				.findWithStudyResultId(1l);
		checkDefaultIdCookie(idCookie1Extracted);
		assertThat(RequestScope.has(IdCookieCollection.class.getSimpleName()))
				.isTrue();
	}

	@Test
	public void checkDiscard() throws IdCookieAlreadyExistsException {
		IdCookie idCookie1 = IdCookieTestHelper.buildDummyIdCookie(1l);
		List<Cookie> cookieList = new ArrayList<>();
		cookieList.add(buildCookie(idCookie1));

		mockContext(cookieList);

		// extract() puts it in the RequestScope too
		IdCookieCollection idCookieCollection = idCookieAccessor.extract();
		assertThat(idCookieCollection.size()).isEqualTo(1);

		idCookieAccessor.discard(1l);

		// Check in RequestScope
		idCookieCollection = (IdCookieCollection) RequestScope
				.get(IdCookieCollection.class.getSimpleName());
		assertThat(idCookieCollection.size()).isEqualTo(0);

		// Check in Response object
		// TODO How to check response?
		// assertThat(Http.Context.current.get().response().cookie("JATOS_IDS_0"))
		// .isNull();
	}

}
