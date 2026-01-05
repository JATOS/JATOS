package general.common;

import play.api.mvc.DiscardingCookie;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http.*;
import play.routing.Router;
import scala.Option;
import scala.jdk.CollectionConverters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Defines HTTP standard objects.
 */
public class Http {

    /**
     * The global HTTP context.
     */
    public static class Context {

        public static ThreadLocal<Context> current = new ThreadLocal<>();

        /**
         * Retrieves the current HTTP context, for the current thread.
         *
         * @return the context
         */
        public static Context current() {
            Context c = current.get();
            if (c == null) {
                throw new RuntimeException("There is no HTTP Context available from here.");
            }
            return c;
        }

        /**
         * Safely sets the current HTTP context, for the current thread. Does nothing is the context thread local is
         * disabled.
         */
        public static void setCurrent(Http.Context ctx) {
            Context.current.set(ctx);
        }

        /**
         * Safely removes the current HTTP context, for the current thread. Does nothing is the context thread local is
         * disabled.
         */
        public static void clear() {
            Context.current.remove();
        }

        private final Long id;
        private final RequestHeader requestHeader;
        private final Response response;
        private final Session session;
        private final Flash flash;
        private final Args args;

        /**
         * Creates a new HTTP context.
         *
         * @param requestHeader the HTTP request
         */
        public Context(RequestHeader requestHeader) {
            this.requestHeader = requestHeader;
            this.id = requestHeader.asScala().id();
            this.response = new Response();
            this.session = requestHeader.session();
            this.flash = requestHeader.flash();
            this.args = new Args();
        }

        /**
         * The context id (unique)
         *
         * @return the id
         */
        public Long id() {
            return id;
        }

        /**
         * Returns the current request.
         *
         * @return the request
         */
        public RequestHeader requestHeader() {
            return requestHeader;
        }

        /**
         * Returns the current response.
         *
         * @return the response
         */
        public Response response() {
            return response;
        }

        /**
         * Returns the current session.
         *
         * @return the session
         */
        public Session session() {
            return session;
        }

        /**
         * Returns the current flash scope.
         *
         * @return the flash scope
         */
        public Flash flash() {
            return flash;
        }

        /**
         * Free space to store your request specific data.
         */
        public Args args() {
            return args;
        }

        /**
         * @return a String representation
         */
        public String toString() {
            return "Context attached to (" + requestHeader() + ")";
        }

        /**
         * Helper to propagate the current context through a CompletionStage. This version only handles restoration. The
         * caller must do cleanup.
         */
        public static <T> CompletionStage<T> withContext(CompletionStage<T> stage) {
            Context captured = current();
            return stage.whenComplete((r, t) -> setCurrent(captured));
        }

        /**
         * Overload that allows running a 'before' block synchronously.
         */
        public static <T> CompletionStage<T> withContext(Runnable before, CompletionStage<T> stage) {
            if (before != null) {
                before.run();
            }
            return withContext(stage);
        }

        /**
         * Runs a block of code in a different thread (via the provided executor) while propagating the current HTTP
         * Context.
         */
        public static <T> CompletableFuture<T> withContext(Executor executor, Supplier<T> block) {
            Context captured = current();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    setCurrent(captured);
                    return block.get();
                } finally {
                    clear();
                }
            }, executor);
        }

        /**
         * Retrieves an annotation of a specific type from the request's handler (controller or method).
         */
        public static <A extends Annotation> Optional<A> getAnnotation(RequestHeader requestHeader, Class<A> annotationClass) {
            return requestHeader.attrs().getOptional(Router.Attrs.HANDLER_DEF).flatMap(handlerDef -> {
                try {
                    Class<?> controllerClass = Class.forName(handlerDef.controller());
                    A classAnn = controllerClass.getAnnotation(annotationClass);

                    List<Class<?>> paramTypes = CollectionConverters.SeqHasAsJava(handlerDef.parameterTypes()).asJava();
                    Method method = controllerClass.getMethod(handlerDef.method(), paramTypes.toArray(new Class[0]));
                    A methodAnn = method.getAnnotation(annotationClass);

                    return Optional.ofNullable(methodAnn != null ? methodAnn : classAnn);
                } catch (Exception e) {
                    return Optional.empty();
                }
            });
        }
    }

    /**
     * The HTTP response.
     */
    public static class Response implements HeaderNames {

        private final Map<String, String> headers = new TreeMap<>(String::compareToIgnoreCase);
        private final List<Cookie> cookies = new ArrayList<>();

        /**
         * Adds a new header to the response.
         *
         * @param name  The name of the header, must not be null
         * @param value The value of the header, must not be null
         */
        public void setHeader(String name, String value) {
            if (name == null) {
                throw new NullPointerException("Header name cannot be null!");
            }
            if (value == null) {
                throw new NullPointerException("Header value cannot be null!");
            }
            this.headers.put(name, value);
        }

        /**
         * Gets the current response headers.
         *
         * @return the current response headers.
         */
        public Map<String, String> getHeaders() {
            return headers;
        }

        /**
         * Set a new cookie.
         *
         * @param cookie to set
         */
        public void setCookie(Cookie cookie) {
            cookies.add(cookie);
        }

        /**
         * Discard a cookie on the default path ("/") with no domain and that's not secure.
         *
         * @param name The name of the cookie to discard, must not be null
         */
        public void discardCookie(String name) {
            discardCookie(name, "/", null, false);
        }

        /**
         * Discard a cookie on the given path with no domain and not that's secure.
         *
         * @param name The name of the cookie to discard, must not be null
         * @param path The path of the cookie te discard, may be null
         */
        public void discardCookie(String name, String path) {
            discardCookie(name, path, null, false);
        }

        /**
         * Discard a cookie on the given path and domain that's not secure.
         *
         * @param name   The name of the cookie to discard, must not be null
         * @param path   The path of the cookie te discard, may be null
         * @param domain The domain of the cookie to discard, may be null
         */
        public void discardCookie(String name, String path, String domain) {
            discardCookie(name, path, domain, false);
        }

        /**
         * Discard a cookie in this result
         *
         * @param name   The name of the cookie to discard, must not be null
         * @param path   The path of the cookie te discard, may be null
         * @param domain The domain of the cookie to discard, may be null
         * @param secure Whether the cookie to discard is secure
         */
        public void discardCookie(String name, String path, String domain, boolean secure) {
            cookies.add(
                    new DiscardingCookie(name, path, Option.apply(domain), secure).toCookie().asJava());
        }

        public Collection<Cookie> cookies() {
            return cookies;
        }

        public Optional<Cookie> cookie(String name) {
            return cookies.stream().filter(x -> x.name().equals(name)).findFirst();
        }
    }

    public static class Args {

        private TypedMap args = TypedMap.empty();

        // Set the entire map (useful for async propagation)
        public void set(TypedMap map) {
            args = map;
        }

        // Get the entire map
        public TypedMap get() {
            return args;
        }

        // Typesafe helper to get a specific value
        public <T> T get(TypedKey<T> key) {
            return args.get(key);
        }

        // Since TypedMap is immutable, adding a value requires re-setting the ThreadLocal
        public <T> void put(TypedKey<T> key, T value) {
            set(args.put(key, value));
        }

        public boolean containsKey(TypedKey<?> key) {
            return args.containsKey(key);
        }

        public void clear() {
            args.remove();
        }
    }

}