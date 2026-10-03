package filters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.Helpers;
import play.test.TestServer;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Real generated routes, filters, database authentication, and browser-style cookie handling. */
public class ResponsesAuthenticationIntegrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void rootDeployment() throws Exception { checkDeployment("/"); }
    @Test public void prefixedDeployment() throws Exception { checkDeployment("/release-check/"); }

    private GuiceApplicationBuilder application(String prefix) {
        String root = temp.getRoot().getAbsolutePath();
        return new GuiceApplicationBuilder()
                .configure("db.default.url", "jdbc:h2:mem:responses-" + UUID.randomUUID() +
                        ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
                .configure("play.http.context", prefix)
                .configure("jatos.urlBasePath", prefix)
                .configure("jatos.user.admin.password", "ReleaseCheck-Only-42!")
                .configure("jatos.studyAssetsRootPath", root + "/assets")
                .configure("jatos.studyLogs.path", root + "/study-logs")
                .configure("jatos.resultUploads.path", root + "/uploads")
                .configure("jatos.logs.path", root + "/logs")
                .configure("jatos.tmpPath", root + "/tmp");
    }

    @Test public void secureCookiesForProxyDeployment() throws Exception {
        Application app = application("/release-check/")
                .configure("play.http.session.secure", true)
                .configure("play.http.flash.secure", true)
                .configure("play.http.session.path", "/release-check/")
                .configure("play.http.flash.path", "/release-check/")
                .build();
        TestServer server = Helpers.testServer(0, app);
        Helpers.start(server);
        try (HttpClient client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.getRunningHttpPort().orElseThrow() + "/release-check/jatos";
            // Simulate a TLS-terminating proxy; this does not test the proxy or TLS itself.
            for (String path : new String[]{"/signin", ""}) {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(20)).header("Accept", "text/html")
                        .header("X-Forwarded-Proto", "https").GET().build(), HttpResponse.BodyHandlers.ofString());
                String name = path.isEmpty() ? "PLAY_FLASH=" : "PLAY_SESSION=";
                String cookie = response.headers().allValues("Set-Cookie").stream()
                        .filter(v -> v.startsWith(name)).findFirst().orElseThrow().toLowerCase();
                assertTrue(cookie.contains("secure"));
                assertTrue(cookie.contains("httponly"));
                assertTrue(cookie.contains("samesite=lax"));
                assertTrue(cookie.contains("path=/release-check/"));
            }
        } finally {
            Helpers.stop(server);
        }
    }

    private void checkDeployment(String prefix) throws Exception {
        Application app = application(prefix).build();
        TestServer server = Helpers.testServer(0, app);
        Helpers.start(server);
        CookieManager cookies = new CookieManager();
        try (HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).build();
             HttpClient anonymous = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.getRunningHttpPort().orElseThrow() + prefix + "jatos";
            HttpResponse<String> denied = get(anonymous, base, "text/html");
            assertEquals(303, denied.statusCode());
            assertEquals(prefix + "jatos/signin", denied.headers().firstValue("Location").orElseThrow());
            assertEquals(401, get(anonymous, base, "application/json").statusCode());

            HttpResponse<String> page = get(browser, base + "/signin", "text/html");
            assertEquals(200, page.statusCode());
            assertEquals("no-cache, no-store, must-revalidate", page.headers().firstValue("Cache-Control").orElse(""));
            assertEquals("SAMEORIGIN", page.headers().firstValue("X-Frame-Options").orElseThrow());
            assertTrue(page.headers().firstValue("Content-Security-Policy").orElseThrow().contains("nonce-"));
            var matcher = Pattern.compile("'Csrf-Token': '([^']+)'").matcher(page.body());
            assertTrue("Signin page supplies a CSRF token", matcher.find());
            String csrf = matcher.group(1);
            String sessionHeader = page.headers().allValues("Set-Cookie").stream()
                    .filter(v -> v.startsWith("PLAY_SESSION=")).findFirst().orElseThrow();
            assertTrue(sessionHeader.toLowerCase().contains("httponly"));
            assertTrue(sessionHeader.contains("Path=" + app.config().getString("play.http.session.path")));
            assertTrue(sessionHeader.toLowerCase().contains("samesite=lax"));

            assertEquals(403, post(browser, base, null, "username=admin&password=wrong").statusCode());
            assertEquals(403, post(browser, base, "invalid-token", "username=admin&password=wrong").statusCode());
            assertEquals(401, post(browser, base, csrf, "username=admin&password=wrong").statusCode());
            HttpResponse<String> login = post(browser, base, csrf,
                    "username=admin&password=ReleaseCheck-Only-42%21&keepSignedin=false");
            assertEquals(login.body(), 200, login.statusCode());
            assertTrue(login.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
            assertEquals(prefix + "jatos", Json.parse(login.body()).get("redirectUrl").asText());
            assertEquals(200, get(browser, base, "text/html").statusCode());
            HttpResponse<String> homeAlias = get(browser, base.substring(0, base.length() - 5), "text/html");
            assertEquals(200, homeAlias.statusCode());
            assertTrue(homeAlias.headers().firstValue("Cache-Control").orElse("").contains("no-store"));

            var userDao = app.injector().instanceOf(daos.common.UserDao.class);
            var admin = userDao.findByUsernameWithStudies("admin");
            var token = app.injector().instanceOf(services.gui.ApiTokenService.class).create(admin, "release-check", 0);
            String tokenUrl = base + "/api/v1/admin/token";
            assertEquals(200, bearer(anonymous, tokenUrl, token.getRight()).statusCode());
            token.getLeft().setActive(false);
            app.injector().instanceOf(daos.common.ApiTokenDao.class).merge(token.getLeft());
            assertEquals(401, bearer(anonymous, tokenUrl, token.getRight()).statusCode());

            // Permission denial must not log the user out.
            admin.removeRole(models.common.User.Role.ADMIN);
            userDao.merge(admin);
            assertEquals(403, get(browser, base + "/admin", "application/json").statusCode());
            assertEquals(200, get(browser, base, "text/html").statusCode());

            // Signed session corruption must not authenticate, even with a plausible cookie name.
            HttpResponse<String> tampered = anonymous.send(HttpRequest.newBuilder(URI.create(base))
                    .header("Accept", "application/json").header("Cookie", "PLAY_SESSION=tampered")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, tampered.statusCode());
            HttpResponse<String> badToken = browser.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/admin/token"))
                    .header("Authorization", "Bearer invalid-token").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, badToken.statusCode());
            assertTrue(badToken.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));

            HttpResponse<String> logout = get(browser, base + "/signout", "text/html");
            assertEquals(303, logout.statusCode());
            assertEquals(prefix + "jatos/signin", logout.headers().firstValue("Location").orElseThrow());
            assertTrue("Logout discards the session", logout.headers().allValues("Set-Cookie").stream()
                    .anyMatch(v -> v.startsWith("PLAY_SESSION=") && v.toLowerCase().contains("max-age=0")));
            HttpResponse<String> flashed = get(browser, base + "/signin", "text/html");
            assertTrue(flashed.body().contains("been signed out."));
            assertFalse(get(browser, base + "/signin", "text/html").body().contains("been signed out."));
            assertEquals(401, get(browser, base, "application/json").statusCode());
        } finally {
            Helpers.stop(server);
        }
    }

    private HttpResponse<String> bearer(HttpClient client, String url, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String url, String accept) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", accept).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String base, String csrf, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "/signin/local")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (csrf != null) request.header("Csrf-Token", csrf);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
