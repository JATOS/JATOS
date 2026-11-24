package services.gui.importexport;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import exceptions.common.BadRequestException;
import general.common.Common;
import http.common.HttpUtils;
import models.common.User;
import models.gui.StudySourceProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import utils.common.GitCredentialEncryption;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitHttpsAccessTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private HttpsServer server;
    private Git git;
    private Path tmp;
    private String url;
    private TrustManager[] trust;
    private final AtomicInteger authenticated = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean redirect;
    private volatile boolean slow;

    @Before
    public void setup() throws Exception {
        tmp = temporary.newFolder("tmp").toPath();
        Path keys = temporary.getRoot().toPath().resolve("https.p12");
        Process generator = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12",
                "-keystore", keys.toString(), "-storepass", "changeit", "-keypass", "changeit", "-noprompt")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, generator.waitFor());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keys)) {store.load(input, "changeit".toCharArray());}
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, "changeit".toCharArray());
        TrustManagerFactory trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trusts.init(store);
        trust = trusts.getTrustManagers();
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(managers.getKeyManagers(), trust, null);
        Path working = temporary.newFolder("repository").toPath();
        git = Git.init().setDirectory(working.toFile()).setInitialBranch("main").call();
        Files.createDirectory(working.resolve("assets"));
        Files.writeString(working.resolve("assets/index.html"), "HTTPS study");
        git.add().addFilepattern(".").call();
        var commit = git.commit().setMessage("Initial").setAuthor("Test", "test@example.org").setCommitter("Test", "test@example.org").call();
        Path repository = git.getRepository().getDirectory().toPath();
        Files.createDirectories(repository.resolve("info"));
        Files.writeString(repository.resolve("info/refs"), commit.name() + "\trefs/heads/main\n");
        server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                if (redirect) {
                    exchange.getResponseHeaders().set("Location", "http://localhost:" + server.getAddress().getPort() + "/elsewhere");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }
                if (slow) {
                    try {Thread.sleep(2200);} catch (InterruptedException e) {Thread.currentThread().interrupt();}
                }
                String expected = "Basic " + Base64.getEncoder().encodeToString("user:token".getBytes(StandardCharsets.UTF_8));
                if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=git");
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                authenticated.incrementAndGet();
                String path = exchange.getRequestURI().getPath();
                if (!path.startsWith("/repo.git/")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Path file = repository.resolve(path.substring("/repo.git/".length())).normalize();
                if (!file.startsWith(repository) || !Files.isRegularFile(file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = Files.readAllBytes(file);
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        url = "https://localhost:" + server.getAddress().getPort() + "/repo.git";
    }

    @After
    public void stop() {
        if (server != null) server.stop(0);
        if (git != null) git.close();
    }

    private GitCredentialsResolver.ResolvedCredentials credentials(String secret) {
        var props = new StudySourceProperties();
        props.setGitUrl(url);
        props.setUsername("user");
        props.setSecret(secret);
        return new GitCredentialsResolver(mock(GitCredentialEncryption.class)).forStudyCreation(props, new User("user", "User", "u@example.org"));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private org.mockito.MockedStatic<Common> configuration(int timeout) {
        var common = mockStatic(Common.class);
        common.when(Common::getTmpPath).thenReturn(tmp.toString());
        common.when(Common::getStudySourcesGitTimeout).thenReturn(java.time.Duration.ofSeconds(timeout));
        return common;
    }

    private GitStudySourceIO trustedIO() {
        return new GitStudySourceIO() {
            @Override
            protected Transport openTransport(Repository repository, String url) throws IOException {
                TransportHttp http = (TransportHttp) super.openTransport(repository, url);
                assertEquals("false", repository.getConfig().getString("http", null, "followRedirects"));
                assertTrue(repository.getConfig().getBoolean("http", null, "sslVerify", false));
                HttpConnectionFactory delegate = new HttpConnectionFactory() {
                    private final JDKHttpConnectionFactory jdk = new JDKHttpConnectionFactory();

                    @Override
                    public HttpConnection create(URL url) throws IOException {
                        return configure(jdk.create(url));
                    }

                    @Override
                    public HttpConnection create(URL url, Proxy proxy) throws IOException {
                        return configure(jdk.create(url, proxy));
                    }

                    private HttpConnection configure(HttpConnection connection) throws IOException {
                        try {
                            connection.configure(null, trust, null);
                            return connection;
                        } catch (java.security.GeneralSecurityException e) {throw new IOException(e);}
                    }
                };
                http.setHttpConnectionFactory(new GitStudySourceIO.OriginConnectionFactory(HttpUtils.getGitOriginFromRepositoryUrl(url), delegate));
                return http;
            }
        };
    }

    private void noStaging() throws IOException {
        try (var files = Files.list(tmp)) {assertEquals(0, files.count());}
    }

    @Test
    public void fetchesPrivateHttpsRepositoryAndRejectsWrongCredentials() throws Exception {
        try (var ignored = configuration(15); var access = credentials("token")) {
            try (var fetched = trustedIO().stage(url, "", access)) {
                assertEquals("HTTPS study", Files.readString(fetched.content().getAssets().orElseThrow().resolve("index.html")));
            }
            assertTrue(authenticated.get() > 0);
            noStaging();
        }
        authenticated.set(0);
        try (var ignored = configuration(15); var access = credentials("wrong-private-secret")) {
            var error = assertThrows(BadRequestException.class, () -> trustedIO().stage(url, "main", access));
            assertEquals(0, authenticated.get());
            assertFalse(error.getMessage().contains("wrong-private-secret"));
            noStaging();
        }
    }

    @Test
    public void rejectsRedirectsAndUntrustedCertificatesAndTimesOut() throws Exception {
        try (var ignored = configuration(15); var access = credentials("token")) {
            assertThrows(BadRequestException.class, () -> new GitStudySourceIO().stage(url, "main", access));
            noStaging();
            redirect = true;
            requests.set(0);
            assertThrows(BadRequestException.class, () -> trustedIO().stage(url, "main", access));
            assertEquals(1, requests.get());
            noStaging();
        }
        redirect = false;
        slow = true;
        try (var ignored = configuration(1); var access = credentials("token")) {
            var error = assertThrows(BadRequestException.class, () -> trustedIO().stage(url, "main", access));
            assertTrue(error.getMessage(), error.getMessage().contains("timed out"));
            noStaging();
        }
    }

    @Test
    public void secondaryRequestsCannotSendHeadersToAnotherOrigin() {
        var delegate = mock(HttpConnectionFactory.class);
        var factory = new GitStudySourceIO.OriginConnectionFactory("https://example.org", delegate);
        assertThrows(IOException.class, () -> factory.create(URI.create("https://other.example.org/objects/info/alternates").toURL()));
        assertThrows(IOException.class, () -> factory.create(URI.create("https://example.org:8443/objects/abc").toURL()));
        assertThrows(IOException.class, () -> factory.create(URI.create("http://example.org/objects/abc").toURL()));
        verifyNoInteractions(delegate);
    }
}
