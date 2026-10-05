package studyrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.StudyResultDao;
import models.common.Study;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Checks a study run of an example study (Potato Compass).
 */
public class StudyRunIntegrationTest extends JatosTest {
    /**
     * Runs the imported study through {@code test/resources/studyrun/browser.cjs}
     * against a test server. Playwright exercises Chromium, Firefox and WebKit with the current standard
     * client, current minified client and an abort scenario: nine study runs in total.
     *
     * <p>The browser script checks initialization and legacy input data, Unicode result submission and
     * append, file upload/download, session persistence across three components, completion and abort,
     * and uncaught JavaScript errors. It calls the {@code jatos.js} API directly rather than clicking
     * through the participant interface. This Java test then verifies persisted run states and results;
     * session data is expected to be cleared on completion.</p>
     *
     * <p>Requires Node.js on PATH, {@code JATOS_PLAYWRIGHT_MODULE} pointing to an installed
     * {@code @playwright/test} package, and its three browser binaries already installed. No downloads
     * are performed. Without the environment variable, JUnit skips this test. Browser output is retained
     * in a temporary log whose path is printed for diagnosis.</p>
     */
    @Test public void legacyStudyRunsInRealBrowsers() throws Exception {
        assumeTrue("Set JATOS_PLAYWRIGHT_MODULE for the browser check",
                System.getenv("JATOS_PLAYWRIGHT_MODULE") != null);
        Study study = importAndGetExampleStudy();
        WSResponse codes = api("/jatos/api/v1/studies/" + study.getId() + "/studyCodes")
                .post(Json.newObject().put("type", "PersonalSingle").put("amount", 9));
        assertEquals(200, codes.getStatus());
        ObjectNode config = Json.newObject();
        String url = api("/").raw().getUrl();
        config.put("base", url.substring(0, url.length() - 1));
        var codeArray = config.putArray("codes");
        codes.asJson().get("data").forEach(code -> codeArray.add(code.asText()));
        Path input = Files.createTempFile("jatos-browser-input-", ".json");
        Path output = Files.createTempFile("jatos-browser-output-", ".json");
        Path log = Files.createTempFile("jatos-browser-log-", ".txt");
        config.put("output", output.toString());
        Files.writeString(input, config.toString());
        Process process = null;
        try {
            process = new ProcessBuilder("node", "test/resources/studyrun/browser.cjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue("Browser timeout; log: " + log, process.waitFor(240, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            JsonNode runs = Json.parse(Files.readString(output));
            assertEquals(9, runs.size());
            var dataDao = application.injector().instanceOf(daos.common.ComponentResultDao.class);
            var dao = application.injector().instanceOf(StudyResultDao.class);
            for (JsonNode run : runs) {
                jpaApi.withTransaction(em -> {
                    var result = dao.findByUuid(run.get("uuid").asText()).orElseThrow();
                    if (run.get("mode").asText().equals("abort")) {
                        assertEquals("ABORTED", result.getStudyState().name());
                    } else {
                        assertEquals("FINISHED", result.getStudyState().name());
                        // Session persistence across components is checked in the browser; finishing clears it.
                        assertNull(result.getStudySessionData());
                        assertEquals(3, result.getComponentResultList().size());
                        assertEquals("{\"text\":\"Grüße 日本語 😀\",\"score\":7}\nappend", dataDao.getData(result.getComponentResultList().get(0).getId()));
                        assertEquals("{\"component\":3}", dataDao.getData(result.getComponentResultList().get(2).getId()));
                    }
                    return null;
                });
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            System.out.println("Study compatibility browser log: " + log);
        }
    }

}
