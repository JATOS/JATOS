package studyrun;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import general.common.Common;
import org.junit.Test;
import play.libs.Json;
import testutils.JatosTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Opt-in reliability exercise against JatosTest's isolated database and asset directories.
 * Requires JATOS_PLAYWRIGHT_MODULE, Node.js and installed Chromium. Six simultaneous clients
 * reconnect twice and recover from a request interrupted before delivery. Database hashes verify
 * result isolation; full and cancelled HTTP exports exercise streaming and temporary-file cleanup.
 * This is a bounded regression exercise, not a production capacity benchmark.
 */
public class StudyRunReliabilityIntegrationTest extends JatosTest {
    @Test public void concurrentRunsReconnectAndExportsReleaseResources() throws Exception {
        assumeTrue(System.getenv("JATOS_PLAYWRIGHT_MODULE") != null);
        var study = importAndGetExampleStudy();
        var codes = api("/jatos/api/v1/studies/" + study.getId() + "/studyCodes")
                .post(Json.newObject().put("type", "PersonalSingle").put("amount", 6));
        assertEquals(200, codes.getStatus());
        String base = api("/").raw().getUrl().replaceAll("/$", "");
        Path input = Files.createTempFile("jatos-reliability-", ".json");
        Path output = Files.createTempFile("jatos-reliability-output-", ".json");
        Path log = Files.createTempFile("jatos-reliability-browser-", ".log");
        var config = Json.newObject().put("base", base).put("output", output.toString());
        config.set("codes", codes.asJson().get("data"));
        Files.writeString(input, config.toString());
        Process process = null;
        try {
            process = new ProcessBuilder("node", "test/resources/studyrun/reliability.cjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue("Browser timeout: " + log, process.waitFor(180, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            JsonNode runs = Json.parse(Files.readString(output));
            assertEquals(6, runs.size());
            var resultDao = application.injector().instanceOf(StudyResultDao.class);
            var dataDao = application.injector().instanceOf(ComponentResultDao.class);
            for (JsonNode run : runs) {
                String data = jpaApi.withTransaction(em -> {
                    var result = resultDao.findByUuid(run.get("uuid").asText()).orElseThrow();
                    assertEquals("FINISHED", result.getStudyState().name());
                    assertEquals(1, result.getComponentResultList().size());
                    return dataDao.getData(result.getComponentResultList().getFirst().getId());
                });
                assertEquals(run.get("length").asInt(), data.length());
                assertEquals(run.get("hash").asText(), hash(data.getBytes(StandardCharsets.UTF_8)));
            }
            var batchDao = application.injector().instanceOf(daos.common.BatchDao.class);
            JsonNode batchData = Json.parse(batchDao.findById(study.getDefaultBatch().getId()).getBatchSessionData());
            for (int i = 0; i < 6; i++) assertEquals(i, batchData.get("client" + i).asInt());
            // Closed browser contexts must unregister all batch channels.
            Object dispatcher = application.injector().instanceOf(batch.BatchDispatcher.class);
            var field = dispatcher.getClass().getDeclaredField("channelsByBatch");
            field.setAccessible(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean empty;
            do {
                synchronized (dispatcher) {
                    empty = ((scala.collection.Map<?, ?>) field.get(dispatcher)).isEmpty();
                }
                if (!empty) Thread.sleep(50);
            } while (!empty && System.nanoTime() < deadline);
            assertTrue("Batch channels retained after browser closure", empty);

            // Incompressible asset makes cancellation happen during a substantial transfer.
            byte[] large = new byte[32 * 1024 * 1024];
            new java.util.Random(42).nextBytes(large);
            Path asset = Path.of(Common.getStudyAssetsRootPath(), study.getDirName(), "reliability.bin");
            Files.write(asset, large);
            String expectedHash = hash(large);
            Set<Path> baseline = exportTempFiles();
            try (HttpClient client = HttpClient.newHttpClient()) {
                String studyUrl = base + "/jatos/api/v1/studies/" + study.getId();
                for (int i = 0; i < 3; i++) {
                    try (var body = client.send(request(studyUrl), HttpResponse.BodyHandlers.ofInputStream()).body()) {
                        assertTrue(body.readNBytes(1024).length > 0);
                    }
                    awaitTempCleanup(baseline);
                }
                var response = client.send(request(studyUrl), HttpResponse.BodyHandlers.ofInputStream());
                assertEquals(200, response.statusCode());
                boolean found = false;
                try (ZipInputStream zip = new ZipInputStream(response.body())) {
                    for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (entry.getName().endsWith("/reliability.bin")) {
                            assertEquals(expectedHash, hash(zip.readAllBytes()));
                            found = true;
                        }
                    }
                }
                assertTrue("Large asset missing from export", found);
                awaitTempCleanup(baseline);
                String resultsUrl = base + "/jatos/api/v1/results?studyId=" + study.getId();
                for (int i = 0; i < 3; i++) {
                    try (var body = client.send(request(resultsUrl), HttpResponse.BodyHandlers.ofInputStream()).body()) {
                        assertTrue(body.readNBytes(1024).length > 0);
                    }
                    awaitTempCleanup(baseline);
                }
                var results = client.send(request(resultsUrl), HttpResponse.BodyHandlers.ofInputStream());
                assertEquals(200, results.statusCode());
                Set<String> expected = new HashSet<>();
                runs.forEach(run -> expected.add(run.get("hash").asText()));
                Set<String> actual = new HashSet<>();
                boolean metadata = false;
                try (ZipInputStream zip = new ZipInputStream(results.body())) {
                    for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (entry.getName().endsWith("/data.txt")) actual.add(hash(zip.readAllBytes()));
                        if (entry.getName().equals("metadata.json")) {
                            assertNotNull(Json.parse(zip.readAllBytes()).get("data"));
                            metadata = true;
                        }
                    }
                }
                assertEquals(expected, actual);
                assertTrue(metadata);
                awaitTempCleanup(baseline);
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            System.out.println("Reliability browser log: " + log);
        }
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiToken).build();
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private Set<Path> exportTempFiles() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(p -> p.getFileName().toString().matches("metadata[0-9]+json|jatos_study_.*"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
    private void awaitTempCleanup(Set<Path> baseline) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Set<Path> remaining;
        do {
            remaining = exportTempFiles();
            remaining.removeAll(baseline);
            if (remaining.isEmpty()) return;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertTrue("Export temporary files retained: " + remaining, remaining.isEmpty());
    }
}
