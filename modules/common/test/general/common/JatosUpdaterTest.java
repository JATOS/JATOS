package general.common;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.Environment;
import play.inject.ApplicationLifecycle;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import scala.concurrent.ExecutionContext;
import utils.common.IOUtils;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for JatosUpdater.
 */
@SuppressWarnings("SameParameterValue")
public class JatosUpdaterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static MockedStatic<Common> commonStatic;
    private static MockedStatic<IOUtils> ioUtilsStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void setUpStatics() {
        commonStatic = mockStatic(Common.class);
        ioUtilsStatic = mockStatic(IOUtils.class);
        commonStatic.when(Common::getJatosVersion).thenReturn("v3.9.0");
        commonStatic.when(Common::getBasepath).thenReturn(Path.of(System.getProperty("java.io.tmpdir"), "jatos-base").toString());
        ioUtilsStatic.when(IOUtils::tmpDir).thenReturn(Path.of(System.getProperty("java.io.tmpdir"), "jatos-tmp"));
    }

    @AfterClass
    public static void tearDownStatics() {
        if (ioUtilsStatic != null) ioUtilsStatic.close();
        if (commonStatic != null) commonStatic.close();
    }

    private JatosUpdater newUpdater(WSClient ws) {
        Materializer materializer = mock(Materializer.class);
        ActorSystem actorSystem = mock(ActorSystem.class);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ApplicationLifecycle applicationLifecycle = mock(ApplicationLifecycle.class);
        Environment environment = mock(Environment.class);

        return new JatosUpdater(ws, materializer, actorSystem, executionContext, applicationLifecycle, environment);
    }

    private static JsonNode releaseJson(String tagName, boolean prerelease, String body) throws Exception {
        return MAPPER.readTree("{"
                + "\"tag_name\":\"" + tagName + "\","
                + "\"prerelease\":" + prerelease + ","
                + "\"body\":\"" + body.replace("\"", "\\\"") + "\","
                + "\"assets\":[]"
                + "}");
    }

    @Test
    public void testGetReleaseInfoReturnsCurrentUpdateStateAndResetsSuccessfulState() throws Exception {
        WSClient ws = mock(WSClient.class);
        JatosUpdater updater = newUpdater(ws);
        updater.state = JatosUpdater.UpdateState.SUCCESS;

        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);

        when(ws.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.asJson()).thenReturn(releaseJson("v3.9.1", false, "release notes"));

        JsonNode result = updater.getReleaseInfo(null, false).toCompletableFuture().get();

        assertEquals("SUCCESS", result.get("currentUpdateState").asText());
        assertEquals("v3.9.1", result.get("versionFull").asText());
        assertEquals(JatosUpdater.UpdateState.SLEEPING, updater.state);
    }

    @Test
    public void testGetReleaseInfoUsesCachedLatestReleaseWithinOneHour() throws Exception {
        WSClient ws = mock(WSClient.class);
        JatosUpdater updater = newUpdater(ws);

        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);

        when(ws.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.asJson()).thenReturn(releaseJson("v3.9.1", false, "release notes"));

        JsonNode first = updater.getReleaseInfo(null, false).toCompletableFuture().get();
        JsonNode second = updater.getReleaseInfo(null, false).toCompletableFuture().get();

        assertEquals("v3.9.1", first.get("versionFull").asText());
        assertEquals("v3.9.1", second.get("versionFull").asText());

        Mockito.verify(ws, Mockito.times(1)).url("https://api.github.com/repos/JATOS/JATOS/releases/latest");
    }

    @Test
    public void testDownloadFromGitHubAndUnzipFailsWhenUpdateIsNotAllowed() throws Exception {
        JatosUpdater updater = newUpdater(mock(WSClient.class));
        updater.currentReleaseInfo = createReleaseInfoForAllowedUpdate();
        updater.currentReleaseInfo.isUpdateAllowed = false;

        CompletionStage<?> stage = updater.downloadFromGitHubAndUnzip(false);

        try {
            stage.toCompletableFuture().get();
            fail("Expected update to fail");
        } catch (Exception expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
            assertTrue(expected.getCause().getMessage().contains("updated manually"));
        }
    }

    @Test
    public void testDownloadFromGitHubAndUnzipFailsInWrongState() throws Exception {
        JatosUpdater updater = newUpdater(mock(WSClient.class));
        updater.currentReleaseInfo = createReleaseInfoForAllowedUpdate();
        updater.state = JatosUpdater.UpdateState.DOWNLOADING;

        CompletionStage<?> stage = updater.downloadFromGitHubAndUnzip(false);

        try {
            stage.toCompletableFuture().get();
            fail("Expected update to fail");
        } catch (Exception expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
            assertTrue(expected.getCause().getMessage().contains("already downloading"));
        }
    }

    @Test
    public void testDownloadFromGitHubAndUnzipDryRunMovesStateToDownloaded() throws Exception {
        JatosUpdater updater = newUpdater(mock(WSClient.class));
        updater.currentReleaseInfo = createReleaseInfoForAllowedUpdate();

        updater.downloadFromGitHubAndUnzip(true).toCompletableFuture().get();

        assertEquals(JatosUpdater.UpdateState.DOWNLOADED, updater.state);
    }

    @Test
    public void testUpdateAndRestartRejectsWrongState() throws Exception {
        JatosUpdater updater = newUpdater(mock(WSClient.class));
        updater.currentReleaseInfo = createReleaseInfoForAllowedUpdate();

        try {
            updater.updateAndRestart(false);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Wrong update state"));
        }
    }

    @Test
    public void testCancelUpdateResetsStateToSleeping() {
        JatosUpdater updater = newUpdater(mock(WSClient.class));
        updater.state = JatosUpdater.UpdateState.DOWNLOADED;

        updater.cancelUpdate();

        assertEquals(JatosUpdater.UpdateState.SLEEPING, updater.state);
    }

    private static JatosUpdater.ReleaseInfo createReleaseInfoForAllowedUpdate() throws Exception {
        JsonNode json = releaseJson("v3.9.1", false, "release notes");
        Constructor<JatosUpdater.ReleaseInfo> ctor =
                JatosUpdater.ReleaseInfo.class.getDeclaredConstructor(JsonNode.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(json, true);
    }
}