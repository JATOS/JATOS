package testutils;

import org.mockito.Mockito;
import play.api.mvc.RequestHeader;
import play.mvc.Http;
import play.test.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Helper class that provides methods to mock Play's Context
 */
public class ContextMocker {

    /**
     * Mocks Play's Http.Context without cookies
     */
    public static void mock() {
        Http.Cookies cookies = Mockito.mock(Http.Cookies.class);
        mock(cookies);
    }

    /**
     * Mocks Play's Http.Context with one cookie that can be retrieved by
     * cookies.get(name)
     */
    public static void mock(Http.Cookie cookie) {
        Http.Cookies cookies = Mockito.mock(Http.Cookies.class);
        when(cookies.get(cookie.name())).thenReturn(cookie);
        mock(cookies);
    }

    /**
     * Mocks Play's Http.Context with cookies. The cookies can be retrieved by
     * cookieList.iterator()
     */
    public static void mock(List<Http.Cookie> cookieList) {
        Http.Cookies cookies = Mockito.mock(Http.Cookies.class);
        when(cookies.iterator()).thenReturn(cookieList.iterator());
        mock(cookies);
    }

    private static void mock(Http.Cookies cookies) {
        Map<String, String> flashData = Collections.emptyMap();
        Map<String, Object> argData = Collections.emptyMap();
        Long id = 2L;
        RequestHeader header = Mockito.mock(RequestHeader.class);
        Http.Request request = Mockito.mock(Http.Request.class);
        when(request.cookies()).thenReturn(cookies);
        when(request.queryString()).thenReturn(null);
        Http.Context context = new Http.Context(id, header, request, flashData, flashData, argData,
                Helpers.contextComponents());
        Http.Context.current.set(context);
    }
}
