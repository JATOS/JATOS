package general;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import javax.inject.Singleton;

import play.api.mvc.RequestHeader;
import play.mvc.Http;
import play.mvc.Http.Cookies;

@Singleton
public class TestHelper {

	public void mockContext() {
		Cookies cookies = mock(Cookies.class);
		mockContext(cookies);
	}

	private void mockContext(Cookies cookies) {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		Long id = 2L;
		RequestHeader header = mock(RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		when(request.cookies()).thenReturn(cookies);
		Http.Context context = new Http.Context(id, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
	}
	
}
