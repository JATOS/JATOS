package http.common;

import general.common.Common;
import http.common.Http.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import play.test.Helpers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

public class HttpUtilsTest {

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getJatosUrlBasePath).thenReturn("");
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void isHtmlRequestReturnsTrueForTextHtmlAcceptHeader() {
        Http.Request request = Helpers.fakeRequest()
                .header("Accept", "text/html")
                .build();

        assertTrue(HttpUtils.isHtmlRequest(request));
    }

    @Test
    public void isHtmlRequestReturnsTrueForTextHtmlWithQualityParameter() {
        Http.Request request = Helpers.fakeRequest()
                .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                .build();

        assertTrue(HttpUtils.isHtmlRequest(request));
    }

    @Test
    public void isHtmlRequestIgnoresCaseAndWhitespace() {
        Http.Request request = Helpers.fakeRequest()
                .header("Accept", " application/json , TEXT/HTML ; q=0.8 ")
                .build();

        assertTrue(HttpUtils.isHtmlRequest(request));
    }

    @Test
    public void isHtmlRequestReturnsFalseWithoutTextHtmlAcceptHeader() {
        Http.Request request = Helpers.fakeRequest()
                .header("Accept", "application/json")
                .build();

        assertFalse(HttpUtils.isHtmlRequest(request));
    }

    @Test
    public void isHtmlRequestReturnsFalseWithoutAcceptHeader() {
        Http.Request request = Helpers.fakeRequest().build();

        assertFalse(HttpUtils.isHtmlRequest(request));
    }

    @Test
    public void isGuiUrlReturnsTrueForJatosRootUrl() {
        assertTrue(HttpUtils.isGuiUrl("/jatos"));
    }

    @Test
    public void isGuiUrlReturnsTrueForJatosGuiUrl() {
        assertTrue(HttpUtils.isGuiUrl("/jatos/admin"));
        assertTrue(HttpUtils.isGuiUrl("/jatos/signin"));
        assertTrue(HttpUtils.isGuiUrl("/jatos/study/1"));
    }

    @Test
    public void isGuiUrlReturnsFalseForApiUrl() {
        assertFalse(HttpUtils.isGuiUrl("/jatos/api/v1/studies"));
        assertFalse(HttpUtils.isGuiUrl("/jatos/api"));
    }

    @Test
    public void isGuiUrlReturnsFalseForNullUrl() {
        assertFalse(HttpUtils.isGuiUrl(null));
    }

    @Test
    public void isGuiUrlReturnsFalseForNonJatosUrl() {
        assertFalse(HttpUtils.isGuiUrl("/publix/123/start"));
        assertFalse(HttpUtils.isGuiUrl("/"));
    }

    @Test
    public void isGuiUrlReturnsTrueForJatosRootUrlWithBasePath() {
        assertTrue(HttpUtils.isGuiUrl(jatosUrlBasePath() + "/jatos"));
    }

    @Test
    public void isGuiUrlReturnsTrueForJatosGuiUrlWithBasePath() {
        String base = jatosUrlBasePath();

        assertTrue(HttpUtils.isGuiUrl(base + "/jatos/admin"));
        assertTrue(HttpUtils.isGuiUrl(base + "/jatos/signin"));
        assertTrue(HttpUtils.isGuiUrl(base + "/jatos/study/1"));
    }

    @Test
    public void isGuiUrlReturnsFalseForApiUrlWithBasePath() {
        String base = jatosUrlBasePath();

        assertFalse(HttpUtils.isGuiUrl(base + "/jatos/api/v1/studies"));
        assertFalse(HttpUtils.isGuiUrl(base + "/jatos/api"));
    }

    @Test
    public void isSigninUrlReturnsTrueForSigninUrl() {
        assertTrue(HttpUtils.isSigninUrl("/jatos/signin"));
        assertTrue(HttpUtils.isSigninUrl("/jatos/signin/"));
    }

    @Test
    public void isSigninUrlReturnsFalseForOtherGuiUrls() {
        assertFalse(HttpUtils.isSigninUrl("/jatos"));
        assertFalse(HttpUtils.isSigninUrl("/jatos/admin"));
    }

    @Test
    public void isSigninUrlReturnsFalseForApiUrls() {
        assertFalse(HttpUtils.isSigninUrl("/jatos/api/v1/studies"));
    }

    @Test
    public void isSigninUrlReturnsTrueForSigninUrlWithBasePath() {
        String base = jatosUrlBasePath();

        assertTrue(HttpUtils.isSigninUrl(base + "/jatos/signin"));
        assertTrue(HttpUtils.isSigninUrl(base + "/jatos/signin/"));
    }

    @Test
    public void isSigninUrlReturnsFalseForOtherGuiUrlsWithBasePath() {
        String base = jatosUrlBasePath();

        assertFalse(HttpUtils.isSigninUrl(base + "/jatos"));
        assertFalse(HttpUtils.isSigninUrl(base + "/jatos/admin"));
        assertFalse(HttpUtils.isSigninUrl(base + "/jatos/study/1"));
    }

    @Test
    public void isSigninUrlReturnsFalseForApiUrlsWithBasePath() {
        assertFalse(HttpUtils.isSigninUrl(jatosUrlBasePath() + "/jatos/api/v1/studies"));
    }

    @Test
    public void isApiRequestReturnsTrueForApiPath() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos/api/v1/studies").build();

        assertTrue(HttpUtils.isApiRequest(request));
    }

    @Test
    public void isApiRequestReturnsFalseForGuiPath() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos/admin").build();

        assertFalse(HttpUtils.isApiRequest(request));
    }

    @Test
    public void isApiRequestReturnsTrueForApiPathWithBasePath() {
        Http.Request request = Helpers.fakeRequest("GET", jatosUrlBasePath() + "/jatos/api/v1/studies").build();

        assertTrue(HttpUtils.isApiRequest(request));
    }

    @Test
    public void isApiRequestReturnsFalseForGuiPathWithBasePath() {
        Http.Request request = Helpers.fakeRequest("GET", jatosUrlBasePath() + "/jatos/admin").build();

        assertFalse(HttpUtils.isApiRequest(request));
    }

    @Test
    public void isApiRequestReturnsFalseForApiPrefixWithoutTrailingSlashWithBasePath() {
        Http.Request request = Helpers.fakeRequest("GET", jatosUrlBasePath() + "/jatos/api").build();

        assertFalse(HttpUtils.isApiRequest(request));
    }

    @Test
    public void urlEncodeEncodesSpecialCharacters() {
        assertEquals("hello+world%21", HttpUtils.urlEncode("hello world!"));
        assertEquals("a%2Fb%3Fc%3Dd%26e%3Df", HttpUtils.urlEncode("a/b?c=d&e=f"));
    }

    @Test
    public void urlDecodeDecodesSpecialCharacters() {
        assertEquals("hello world!", HttpUtils.urlDecode("hello+world%21"));
        assertEquals("a/b?c=d&e=f", HttpUtils.urlDecode("a%2Fb%3Fc%3Dd%26e%3Df"));
    }

    @Test
    public void urlDecodeReturnsNullForNullInput() {
        assertNull(HttpUtils.urlDecode(null));
    }

    @Test
    public void getLocalIpAddressReturnsNonEmptyString() {
        String localIpAddress = HttpUtils.getLocalIpAddress();

        assertNotNull(localIpAddress);
        assertFalse(localIpAddress.isEmpty());
    }

    @Test
    public void getQueryParameterReturnsTrimmedValue() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos?name=%20test%20").build();
        setCurrentContext(request);

        assertEquals("test", HttpUtils.getQueryParameter("name"));
    }

    @Test
    public void getQueryParameterReturnsNullForMissingParameter() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos?name=test").build();
        setCurrentContext(request);

        assertNull(HttpUtils.getQueryParameter("missing"));
    }

    @Test
    public void getQueryStringReturnsQueryStringWithQuestionMark() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos?name=test&value=123").build();
        setCurrentContext(request);

        String queryString = HttpUtils.getQueryString();

        assertTrue(queryString.startsWith("?"));
        assertTrue(queryString.contains("name=test"));
        assertTrue(queryString.contains("value=123"));
    }

    @Test
    public void getQueryStringReturnsQuestionMarkForEmptyQueryString() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos").build();
        setCurrentContext(request);

        assertEquals("?", HttpUtils.getQueryString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getQueryStringThrowsForHtmlInQueryString() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos?name=%3Cscript%3Ealert(1)%3C/script%3E").build();
        setCurrentContext(request);

        HttpUtils.getQueryString();
    }

    @Test
    public void hasBearerTokenReturnsTrueForBearerAuthorizationHeaderOnNonGuiUrl() {
        Http.Request request = Helpers.fakeRequest("GET", "/publix/test")
                .header("Authorization", "Bearer token")
                .build();
        setCurrentContext(request);

        assertTrue(HttpUtils.hasBearerToken());
    }

    @Test
    public void hasBearerTokenReturnsFalseWithoutAuthorizationHeader() {
        Http.Request request = Helpers.fakeRequest("GET", "/publix/test").build();
        setCurrentContext(request);

        assertFalse(HttpUtils.hasBearerToken());
    }

    @Test
    public void hasBearerTokenReturnsFalseForNonBearerAuthorizationHeader() {
        Http.Request request = Helpers.fakeRequest("GET", "/publix/test")
                .header("Authorization", "Basic token")
                .build();
        setCurrentContext(request);

        assertFalse(HttpUtils.hasBearerToken());
    }

    @Test
    public void hasBearerTokenReturnsFalseForGuiUrlEvenWithBearerAuthorizationHeader() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos/admin")
                .header("Authorization", "Bearer token")
                .build();
        setCurrentContext(request);

        assertFalse(HttpUtils.hasBearerToken());
    }

    @Test
    public void isSessionCookieRequestReturnsTrueWithNonEmptyPlaySessionCookie() {
        Http.Cookie cookie = Http.Cookie.builder("PLAY_SESSION", "session-data").build();
        Http.Request request = Helpers.fakeRequest("GET", "/jatos")
                .cookie(cookie)
                .build();
        setCurrentContext(request);

        assertTrue(HttpUtils.isSessionCookieRequest());
    }

    @Test
    public void isSessionCookieRequestReturnsFalseWithoutPlaySessionCookie() {
        Http.Request request = Helpers.fakeRequest("GET", "/jatos").build();
        setCurrentContext(request);

        assertFalse(HttpUtils.isSessionCookieRequest());
    }

    @Test
    public void isSessionCookieRequestReturnsFalseWithEmptyPlaySessionCookie() {
        Http.Cookie cookie = Http.Cookie.builder("PLAY_SESSION", "").build();
        Http.Request request = Helpers.fakeRequest("GET", "/jatos")
                .cookie(cookie)
                .build();
        setCurrentContext(request);

        assertFalse(HttpUtils.isSessionCookieRequest());
    }

    private void setCurrentContext(Http.Request request) {
        Context.setCurrent(new Context(request));
    }

    private String jatosUrlBasePath() {
        return Common.getJatosUrlBasePath().replaceAll("/$", "");
    }

}