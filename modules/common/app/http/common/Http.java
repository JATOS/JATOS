package http.common;

import play.api.mvc.DiscardingCookie;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http.*;
import scala.Option;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static http.common.Http.Context.current;

/**
 * Provides a framework for handling HTTP requests and responses. It is primarily used for propagating HTTP-related
 * operations across threads and managing request-specific data in a thread-safe manner.
 *
 * Additionally, it provides a way to store arguments ({@link Args}) within the context of an HTTP request. This allows for
 * passing data between different parts of the application.
 *
 * It is derived from Play Framework's Http.Context class.
 */
@SuppressWarnings("unused")
public class Http {

    /**
     * The global HTTP context.
     */
    public static class Context {

        private final static ThreadLocal<Context> current = new ThreadLocal<>();

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
            current.set(ctx);
        }

        /**
         * Safely removes the current HTTP context, for the current thread. Does nothing is the context thread local is
         * disabled.
         */
        public static void clear() {
            current.remove();
        }

        private final Long id;
        private final RequestHeader requestHeader;
        private final Response response;
        private final Args args;

        /**
         * Creates a new HTTP context.
         *
         * @param requestHeader the HTTP request
         */
        public Context(RequestHeader requestHeader) {
            this.id = requestHeader.asScala().id();
            this.requestHeader = requestHeader;
            this.response = new Response(requestHeader.session());
            this.args = new Args();
        }

        /**
         * The context id (unique)
         */
        public Long id() {
            return id;
        }

        /**
         * Returns the current request.
         */
        public RequestHeader requestHeader() {
            return requestHeader;
        }

        /**
         * Returns the current response.
         */
        public Response response() {
            return response;
        }

        /**
         * Free space to store your request-specific data.
         */
        public Args args() {
            return args;
        }

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
         * Runs a block with the given HTTP Context bound to the current thread. Restores the previously bound context
         * afterward.
         */
        public static <T> T withContext(Context context, Supplier<T> block) {
            Context previous = current.get();
            try {
                setCurrent(context);
                return block.get();
            } finally {
                if (previous != null) {
                    setCurrent(previous);
                } else {
                    clear();
                }
            }
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
    }

    /**
     * Response object that can store headers, cookies, session, and flash data.
     */
    public static class Response implements HeaderNames {

        private final Map<String, String> headers = new TreeMap<>(String::compareToIgnoreCase);
        private final List<Cookie> cookies = new ArrayList<>();
        private Session session;
        private Flash flash;
        private boolean sessionChanged;
        private boolean flashChanged;

        public Response(Session session) {
            this.session = session;
            this.flash = new Flash(new HashMap<>());
        }

        /**
         * Gets the current response headers. Cookies and Play's session are stored separately in the {@link #cookies()}
         * and the {@link #session()} list.
         *
         * @return the current response headers.
         */
        public Map<String, String> headers() {
            return Collections.unmodifiableMap(headers);
        }

        /**
         * Adds a new header to the response. Don't use it for cookies - use {@link #setCookie(Cookie)} instead.
         *
         * @param name  The name of the header (must not be null)
         * @param value The value of the header (must not be null)
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
         * Removes a header from the response.
         */
        public void removeHeader(String name) {
            if (name == null) {
                throw new NullPointerException("Header name cannot be null!");
            }
            this.headers.remove(name);
        }

        /**
         * Removes all headers from the response.
         */
        public void clearHeaders() {
            this.headers.clear();
        }

        /**
         * Gets the current response cookies.
         */
        public Collection<Cookie> cookies() {
            return cookies;
        }

        /**
         * Gets a cookie of the current response by name.
         */
        public Optional<Cookie> cookie(String name) {
            return cookies.stream().filter(x -> x.name().equals(name)).findFirst();
        }

        /**
         * Set a new cookie.
         */
        public void setCookie(Cookie cookie) {
            cookies.add(cookie);
        }

        /**
         * Set new cookies.
         */
        public void setCookies(Cookie... cookies) {
            Collections.addAll(this.cookies, cookies);
        }

        /**
         * Set new cookies.
         */
        public void setCookies(Collection<Cookie> cookies) {
            this.cookies.addAll(cookies);
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
         * @param name The name of the cookie to discard (ust not be null)
         * @param path The path of the cookie te discard (may be null)
         */
        public void discardCookie(String name, String path) {
            discardCookie(name, path, null, false);
        }

        /**
         * Discard a cookie on the given path and domain that's not secure.
         *
         * @param name   The name of the cookie to discard (must not be null)
         * @param path   The path of the cookie te discard (may be null)
         * @param domain The domain of the cookie to discard (may be null)
         */
        public void discardCookie(String name, String path, String domain) {
            discardCookie(name, path, domain, false);
        }

        /**
         * Discard a cookie in this result
         *
         * @param name   The name of the cookie to discard (must not be null)
         * @param path   The path of the cookie te discard (may be null)
         * @param domain The domain of the cookie to discard (may be null)
         * @param secure Whether the cookie to discard is secure
         */
        public void discardCookie(String name, String path, String domain, boolean secure) {
            cookies.add(new DiscardingCookie(name, path, Option.apply(domain), secure).toCookie().asJava());
        }

        /**
         * Returns the response's session. Do not modify the returned session object, use
         * {@link #putSession(String, String)}, {@link #removeSession(String)}, and {@link #clearSession()} instead.
         */
        public Session session() {
            return session;
        }

        /**
         * Sets the response's session.
         */
        private void setSession(Session session) {
            this.session = session;
            this.sessionChanged = true;
        }

        /**
         * Returns true if the response's session was changed during request processing.
         */
        public boolean isSessionChanged() {
            return sessionChanged;
        }

        public void putSession(Map<String, String> map) {
            Map<String, String> data = new HashMap<>(session.data());
            data.putAll(map);
            setSession(new Session(data));
        }

        /**
         * Adds or replaces one value in the response's session.
         */
        public void putSession(String key, String value) {
            Map<String, String> data = new HashMap<>(session.data());
            data.put(key, value);
            setSession(new Session(data));
        }

        /**
         * Removes one value from the response's session.
         */
        public void removeSession(String key) {
            Map<String, String> data = new HashMap<>(session.data());
            data.remove(key);
            setSession(new Session(data));
        }

        /**
         * Clears the response's session.
         */
        public void clearSession() {
            setSession(new Session(new HashMap<>()));
        }

        /**
         * Returns the response's flash scope if it was changed during request processing.
         */
        public Flash flash() {
            return flash;
        }

        /**
         * Sets the response's flash scope for this response.
         */
        private void setFlash(Flash flash) {
            this.flash = flash;
            this.flashChanged = true;
        }

        /**
         * Returns true if the response's flash scope was changed during request processing.
         */
        public boolean isFlashChanged() {
            return flashChanged;
        }

        /**
         * Adds or replaces one value in the response's flash scope.
         *
         * If no outgoing flash scope exists yet, it starts from the current request flash scope.
         */
        public void putFlash(String key, String value) {
            Map<String, String> data = new HashMap<>(current().requestHeader().flash().data());
            if (flash != null) {
                data.putAll(flash.data());
            }
            data.put(key, value);
            setFlash(new Flash(data));
        }

        /**
         * Removes one value from the response's flash scope.
         *
         * If no outgoing flash scope exists yet, it starts from the current request flash scope.
         */
        public void removeFlash(String key) {
            Map<String, String> data = new HashMap<>(current().requestHeader().flash().data());
            if (flash != null) {
                data.putAll(flash.data());
            }
            data.remove(key);
            setFlash(new Flash(data));
        }

        /**
         * Clears the response's flash scope.
         */
        public void clearFlash() {
            setFlash(new Flash(new HashMap<>()));
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

        public <T> Optional<T> getOptional(TypedKey<T> key) {
            return containsKey(key) ? Optional.of(args.get(key)) : Optional.empty();
        }

        // Typesafe helper to get a specific value or return a default value
        public <T> T getOrElse(TypedKey<T> key, T defaultValue) {
            if (args.containsKey(key)) {
                return args.get(key);
            }
            return defaultValue;
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