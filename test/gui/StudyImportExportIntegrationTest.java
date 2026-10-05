package gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import http.common.Http.Context;
import models.common.Study;
import org.junit.Test;
import play.libs.ws.WSResponse;
import services.gui.ImportExportService;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;

/**
 * Checks import/export compatibility of an example study archive (Potato Compass archive), including study assets
 * and properties.
 */
public class StudyImportExportIntegrationTest extends JatosTest {
    /**
     * Imports the archive, exports it through the HTTP API, deletes the study and reimports the export.
     * Compares non-hidden study assets byte-for-byte (excluding the {@code .jas} metadata) and checks study,
     * component and batch properties after reimport, ignoring generated IDs and dates.
     */
    @Test public void legacyArchiveSurvivesExportDeleteAndReimport() throws Exception {
        Study study = importAndGetExampleStudy();
        JsonNode before = properties(study.getId());
        WSResponse exported = api("/jatos/api/v1/studies/" + study.getId()).get();
        assertEquals(200, exported.getStatus());
        assertTrue(exported.getContentType().startsWith("application/zip"));
        Map<String, byte[]> originalAssets = assets(Files.readAllBytes(Path.of("test/resources/potato_compass.jzip")));
        Map<String, byte[]> exportedAssets = assets(exported.asByteArray());
        assertEquals(originalAssets.keySet(), exportedAssets.keySet());
        originalAssets.forEach((name, bytes) -> assertArrayEquals(name, bytes, exportedAssets.get(name)));
        assertEquals(200, api("/jatos/api/v1/studies/" + study.getId()).delete().getStatus());
        Path archive = Files.createTempFile("jatos-study-roundtrip-", ".jzip");
        try {
            Files.write(archive, exported.asByteArray());
            Context.current().args().put(SIGNEDIN_USER, admin);
            ImportExportService service = application.injector().instanceOf(ImportExportService.class);
            try {
                service.importStudy(archive);
                Study restored = service.importStudyConfirmed(false, false, false, false);
                assertEquals(before, properties(restored.getId()));
            } finally { service.cleanupAfterStudyImport(); }
        } finally { Files.deleteIfExists(archive); }
    }

    private JsonNode properties(Long id) {
        WSResponse response = api("/jatos/api/v1/studies/" + id + "/properties")
                .queryParam("withComponentProperties", "true").queryParam("withBatchProperties", "true").get();
        assertEquals(200, response.getStatus());
        JsonNode json = response.asJson().get("data");
        removeGeneratedIds(json);
        return json;
    }
    private void removeGeneratedIds(JsonNode node) {
        if (node.isObject()) ((ObjectNode) node).remove(Arrays.asList("id", "date"));
        node.forEach(this::removeGeneratedIds);
    }
    private Map<String, byte[]> assets(byte[] archive) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (!entry.isDirectory() && !name.endsWith(".jas") && !name.contains("/.")) files.put(name, zip.readAllBytes());
            }
        }
        return files;
    }
}
