package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import general.AbstractTest;
import play.api.mvc.RequestHeader;
import play.db.jpa.JPA;
import play.mvc.Http;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
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

	protected void mockContext(List<Cookie> cookieList) {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		Long id = 2L;
		RequestHeader header = mock(RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Cookies cookies = mock(Cookies.class);
		when(cookies.iterator()).thenReturn(cookieList.iterator());
		when(request.cookies()).thenReturn(cookies);
		Http.Context context = new Http.Context(id, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
		JPA.bindForSync(entityManager);
	}

	public Cookie buildCookie(IdCookie idCookie) {
		String cookieValue = idCookieAccessor.asCookieString(idCookie);
		Cookie cookie = new Cookie(idCookie.getName(), cookieValue,
				Integer.MAX_VALUE, "/", "", false, false);
		return cookie;
	}

	@Test
	public void checkExtract() throws ForbiddenPublixException,
			BadRequestPublixException, IdCookieAlreadyExistsException {
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
}
