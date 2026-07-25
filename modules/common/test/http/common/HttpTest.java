package http.common;

import exceptions.common.JatosException;
import http.common.Http.Context;
import org.junit.After;
import org.junit.Test;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.Http.Session;
import play.test.Helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HttpTest {

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void contextConstructorStoresRequestIdRequestHeaderResponseAndArgs() {
        Request request = Helpers.fakeRequest("GET", "/jatos").build();
        Context context = new Context(request);

        assertEquals(request.asScala().id(), context.id().longValue());
        assertSame(request, context.requestHeader());
        assertNotNull(context.response());
        assertNotNull(context.args());
        assertSame(request.session(), context.response().session());
    }

    @Test
    public void currentReturnsThreadLocalContext() {
        Context context = new Context(Helpers.fakeRequest().build());

        Context.setCurrent(context);

        assertSame(context, Context.current());
    }

    @Test(expected = JatosException.class)
    public void currentThrowsWithoutThreadLocalContext() {
        Context.current();
    }

    @Test
    public void clearRemovesThreadLocalContext() {
        Context context = new Context(Helpers.fakeRequest().build());
        Context.setCurrent(context);

        Context.clear();

        try {
            Context.current();
            fail("Expected JatosException");
        } catch (JatosException e) {
            assertEquals("There is no HTTP Context available from here.", e.getMessage());
        }
    }

    @Test
    public void currentOptionalReturnsContextFromRequestAttribute() {
        Context context = new Context(Helpers.fakeRequest().build());
        RequestHeader request = Helpers.fakeRequest().build()
                .addAttr(Context.CONTEXT_TYPED_KEY, context);

        assertTrue(Context.currentOptional(request).isPresent());
        assertSame(context, Context.currentOptional(request).get());
    }

    @Test
    public void currentOptionalReturnsEmptyWithoutRequestAttribute() {
        Request request = Helpers.fakeRequest().build();

        assertFalse(Context.currentOptional(request).isPresent());
    }

    @Test
    public void currentRequestReturnsContextFromRequestAttribute() {
        Context context = new Context(Helpers.fakeRequest().build());
        RequestHeader request = Helpers.fakeRequest().build()
                .addAttr(Context.CONTEXT_TYPED_KEY, context);

        assertSame(context, Context.current(request));
    }

    @Test(expected = JatosException.class)
    public void currentRequestThrowsWithoutRequestAttribute() {
        Context.current(Helpers.fakeRequest().build());
    }

    @Test
    public void withContextSupplierBindsContextAndRestoresPreviousContext() {
        Context previous = new Context(Helpers.fakeRequest("GET", "/previous").build());
        Context next = new Context(Helpers.fakeRequest("GET", "/next").build());
        Context.setCurrent(previous);

        String result = Context.withContext(next, () -> {
            assertSame(next, Context.current());
            return "done";
        });

        assertEquals("done", result);
        assertSame(previous, Context.current());
    }

    @Test
    public void withContextSupplierClearsContextIfThereWasNoPreviousContext() {
        Context context = new Context(Helpers.fakeRequest().build());

        String result = Context.withContext(context, () -> {
            assertSame(context, Context.current());
            return "done";
        });

        assertEquals("done", result);
        try {
            Context.current();
            fail("Expected JatosException");
        } catch (JatosException e) {
            assertEquals("There is no HTTP Context available from here.", e.getMessage());
        }
    }

    @Test
    public void responseHeadersAreCaseInsensitive() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        response.setHeader("Content-Type", "text/plain");

        assertEquals("text/plain", response.headers().get("content-type"));
        assertEquals("text/plain", response.headers().get("CONTENT-TYPE"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void responseHeadersReturnsUnmodifiableMap() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        response.headers().put("X-Test", "value");
    }

    @Test
    public void responseCanRemoveAndClearHeaders() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        response.setHeader("X-Test", "value");
        response.setHeader("X-Other", "other");
        response.removeHeader("x-test");

        assertFalse(response.headers().containsKey("X-Test"));
        assertTrue(response.headers().containsKey("X-Other"));

        response.clearHeaders();

        assertTrue(response.headers().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void setHeaderThrowsForNullName() {
        new Http.Response(new Session(new HashMap<>())).setHeader(null, "value");
    }

    @Test(expected = NullPointerException.class)
    public void setHeaderThrowsForNullValue() {
        new Http.Response(new Session(new HashMap<>())).setHeader("X-Test", null);
    }

    @Test(expected = NullPointerException.class)
    public void removeHeaderThrowsForNullName() {
        new Http.Response(new Session(new HashMap<>())).removeHeader(null);
    }

    @Test
    public void responseCanSetAndFindCookies() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));
        Cookie cookie = Cookie.builder("test", "value").build();

        response.setCookie(cookie);

        assertEquals(1, response.cookies().size());
        assertTrue(response.cookie("test").isPresent());
        assertSame(cookie, response.cookie("test").get());
        assertFalse(response.cookie("missing").isPresent());
    }

    @Test
    public void responseCanSetMultipleCookies() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));
        Cookie first = Cookie.builder("first", "1").build();
        Cookie second = Cookie.builder("second", "2").build();

        response.setCookies(first, second);

        assertEquals(2, response.cookies().size());
        assertTrue(response.cookie("first").isPresent());
        assertTrue(response.cookie("second").isPresent());
    }

    @Test
    public void responseCanSetCookieCollection() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));
        Cookie first = Cookie.builder("first", "1").build();
        Cookie second = Cookie.builder("second", "2").build();

        response.setCookies(java.util.Arrays.asList(first, second));

        assertEquals(2, response.cookies().size());
        assertTrue(response.cookie("first").isPresent());
        assertTrue(response.cookie("second").isPresent());
    }

    @Test
    public void sessionIsInitiallyUnchanged() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        assertFalse(response.isSessionChanged());
    }

    @Test
    public void putSessionAddsValueAndMarksSessionChanged() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        response.putSession("key", "value");

        assertTrue(response.isSessionChanged());
        assertEquals("value", response.getSession("key").orElse(""));
    }

    @Test
    public void putSessionMapAddsValuesAndMarksSessionChanged() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));
        Map<String, String> values = new HashMap<>();
        values.put("first", "1");
        values.put("second", "2");

        response.putSession(values);

        assertTrue(response.isSessionChanged());
        assertEquals("1", response.getSession("first").orElse(""));
        assertEquals("2", response.getSession("second").orElse(""));
    }

    @Test
    public void removeSessionRemovesValueAndMarksSessionChanged() {
        Map<String, String> initialData = new HashMap<>();
        initialData.put("key", "value");
        Http.Response response = new Http.Response(new Session(initialData));

        response.removeSession("key");

        assertTrue(response.isSessionChanged());
        assertTrue(response.getSession("key").isEmpty());
    }

    @Test
    public void clearSessionRemovesAllSessionValuesAndMarksSessionChanged() {
        Http.Response response = new Http.Response(new Session(Collections.singletonMap("key", "value")));

        response.clearSession();

        assertTrue(response.isSessionChanged());
        assertTrue(response.session().data().isEmpty());
    }

    @Test
    public void flashIsInitiallyUnchanged() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        assertFalse(response.isFlashChanged());
        assertTrue(response.flash().data().isEmpty());
    }

    @Test
    public void putFlashAddsValueAndMarksFlashChanged() {
        Request request = Helpers.fakeRequest().build();
        Context context = new Context(request);
        Context.setCurrent(context);

        context.response().putFlash("key", "value");

        assertTrue(context.response().isFlashChanged());
        assertEquals("value", context.response().flash().get("key").orElse(""));
    }

    @Test
    public void removeFlashRemovesValueAndMarksFlashChanged() {
        Request request = Helpers.fakeRequest()
                .flash("key", "value")
                .build();
        Context context = new Context(request);
        Context.setCurrent(context);

        context.response().removeFlash("key");

        assertTrue(context.response().isFlashChanged());
        assertTrue(context.response().flash().get("key").isEmpty());
    }

    @Test
    public void clearFlashRemovesAllFlashValuesAndMarksFlashChanged() {
        Http.Response response = new Http.Response(new Session(new HashMap<>()));

        response.clearFlash();

        assertTrue(response.isFlashChanged());
        assertTrue(response.flash().data().isEmpty());
    }

    @Test
    public void argsCanStoreRetrieveAndClearTypedValues() {
        Http.Args args = new Http.Args();
        TypedKey<String> key = TypedKey.create("test-key");

        assertFalse(args.containsKey(key));
        assertEquals("default", args.getOrElse(key, "default"));
        assertFalse(args.getOptional(key).isPresent());

        args.put(key, "value");

        assertTrue(args.containsKey(key));
        assertEquals("value", args.get(key));
        assertEquals("value", args.getOptional(key).get());
        assertEquals("value", args.getOrElse(key, "default"));

        args.clear();

        assertFalse(args.containsKey(key));
    }

    @Test
    public void argsCanReplaceTypedMap() {
        Http.Args args = new Http.Args();
        TypedKey<String> key = TypedKey.create("test-key");
        TypedMap map = TypedMap.empty().put(key, "value");

        args.set(map);

        assertSame(map, args.get());
        assertEquals("value", args.get(key));
    }
}