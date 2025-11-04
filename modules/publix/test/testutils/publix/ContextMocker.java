package testutils.publix;

import org.mockito.Mockito;
import play.api.mvc.RequestHeader;
import play.mvc.Http;
import play.test.Helpers;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Helper class that provides methods to mock Play's Context
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class ContextMocker {

    /**
     * Mocks Play's Http.Context without cookies
     */
    public static void mock() {
        mock(Collections.emptyList());
    }

    /**
     * Mocks Play's Http.Context with one cookie
     */
    public static void mock(Http.Cookie cookie) {
        mock(Collections.singletonList(cookie));
    }

    /**
     * Mocks Play's Http.Context with cookies.
     */
    public static void mock(List<Http.Cookie> cookieList) {
        Http.Cookies cookies = Mockito.mock(Http.Cookies.class);
        when(cookies.iterator()).thenReturn(cookieList.iterator());
        cookieList.forEach(cookie -> when(cookies.get(cookie.name())).thenReturn(cookie));
        mock(cookies);
    }

    private static void mock(Http.Cookies cookies) {
        RequestHeader header = Mockito.mock(RequestHeader.class);
        Http.Request request = Mockito.mock(Http.Request.class);
        when(request.cookies()).thenReturn(cookies);
        cookies.forEach(cookie -> when(request.cookie(cookie.name())).thenReturn(cookie));
        when(request.queryString()).thenReturn(null);
        when(request.remoteAddress()).thenReturn("1.2.3.4");
        Http.Context context = new Http.Context(1L, header, request, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Helpers.contextComponents());
        Http.Context.current.set(context);
    }
}
