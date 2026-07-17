package json.common;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.JatosException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import utils.common.HashUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class DirectoryStructureToJsonTest {

    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("directory-structure-to-json-test");
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void getReturnsNestedDirectoryStructure() throws IOException {
        Path file = Files.write(tempDir.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
        Path nestedFile = Files.write(subDir.resolve("nested.txt"), "nested".getBytes(StandardCharsets.UTF_8));

        JsonNode root = DirectoryStructureToJson.get(tempDir, false);

        assertTrue(root.isObject());
        assertEquals(tempDir.getFileName().toString(), root.get("name").asText());
        assertEquals("", root.get("path").asText());
        assertEquals("directory", root.get("type").asText());
        assertFalse(root.has("checksum"));
        assertTrue(root.has("creation"));
        assertTrue(root.has("lastModified"));
        assertTrue(root.has("size"));

        JsonNode content = root.get("content");
        assertTrue(content.isArray());
        assertEquals(2, content.size());

        JsonNode fileNode = findByName(content, "file.txt");
        assertNotNull(fileNode);
        assertEquals("file.txt", fileNode.get("name").asText());
        assertEquals("file.txt", fileNode.get("path").asText());
        assertEquals("file", fileNode.get("type").asText());
        assertEquals(Files.size(file), fileNode.get("size").asLong());
        assertEquals(HashUtils.getChecksum(file), fileNode.get("checksum").asLong());
        assertFalse(fileNode.has("content"));

        JsonNode subDirNode = findByName(content, "subdir");
        assertNotNull(subDirNode);
        assertEquals("subdir", subDirNode.get("name").asText());
        assertEquals("subdir", subDirNode.get("path").asText());
        assertEquals("directory", subDirNode.get("type").asText());
        assertFalse(subDirNode.has("checksum"));

        JsonNode nestedContent = subDirNode.get("content");
        assertTrue(nestedContent.isArray());
        assertEquals(1, nestedContent.size());

        JsonNode nestedFileNode = nestedContent.get(0);
        assertEquals("nested.txt", nestedFileNode.get("name").asText());
        assertEquals("subdir/nested.txt", nestedFileNode.get("path").asText());
        assertEquals("file", nestedFileNode.get("type").asText());
        assertEquals(Files.size(nestedFile), nestedFileNode.get("size").asLong());
        assertEquals(HashUtils.getChecksum(nestedFile), nestedFileNode.get("checksum").asLong());
        assertFalse(nestedFileNode.has("content"));
    }

    @Test
    public void getWithFlattenReturnsOnlyLeafNodes() throws IOException {
        Files.write(tempDir.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
        Files.write(subDir.resolve("nested.txt"), "nested".getBytes(StandardCharsets.UTF_8));

        JsonNode nodes = DirectoryStructureToJson.get(tempDir, true);

        assertTrue(nodes.isArray());
        assertEquals(2, nodes.size());

        JsonNode fileNode = findByName(nodes, "file.txt");
        assertNotNull(fileNode);
        assertEquals("file.txt", fileNode.get("path").asText());
        assertEquals("file", fileNode.get("type").asText());
        assertFalse(fileNode.has("content"));

        JsonNode nestedFileNode = findByName(nodes, "nested.txt");
        assertNotNull(nestedFileNode);
        assertEquals("subdir/nested.txt", nestedFileNode.get("path").asText());
        assertEquals("file", nestedFileNode.get("type").asText());
        assertFalse(nestedFileNode.has("content"));
    }

    @Test
    public void getWithFlattenReturnsEmptyDirectoryAsLeafNode() throws IOException {
        Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));

        JsonNode nodes = DirectoryStructureToJson.get(emptyDir, true);

        assertTrue(nodes.isArray());
        assertEquals(1, nodes.size());

        JsonNode node = nodes.get(0);
        assertEquals("empty", node.get("name").asText());
        assertEquals("", node.get("path").asText());
        assertEquals("directory", node.get("type").asText());
        assertTrue(node.get("content").isArray());
        assertEquals(0, node.get("content").size());
        assertFalse(node.has("checksum"));
    }

    @Test
    public void getReturnsSingleFileNodeForFileBase() throws IOException {
        Path file = Files.write(tempDir.resolve("single.txt"), "single".getBytes(StandardCharsets.UTF_8));

        JsonNode node = DirectoryStructureToJson.get(file, false);

        assertTrue(node.isObject());
        assertEquals("single.txt", node.get("name").asText());
        assertEquals("", node.get("path").asText());
        assertEquals("file", node.get("type").asText());
        assertEquals(Files.size(file), node.get("size").asLong());
        assertEquals(HashUtils.getChecksum(file), node.get("checksum").asLong());
        assertFalse(node.has("content"));
    }

    @Test
    public void getWithFlattenReturnsSingleFileNodeForFileBase() throws IOException {
        Path file = Files.write(tempDir.resolve("single.txt"), "single".getBytes(StandardCharsets.UTF_8));

        JsonNode nodes = DirectoryStructureToJson.get(file, true);

        assertTrue(nodes.isArray());
        assertEquals(1, nodes.size());

        JsonNode node = nodes.get(0);
        assertEquals("single.txt", node.get("name").asText());
        assertEquals("", node.get("path").asText());
        assertEquals("file", node.get("type").asText());
        assertEquals(Files.size(file), node.get("size").asLong());
        assertEquals(HashUtils.getChecksum(file), node.get("checksum").asLong());
        assertFalse(node.has("content"));
    }

    @Test(expected = JatosException.class)
    public void getThrowsJatosExceptionForMissingPath() {
        DirectoryStructureToJson.get(tempDir.resolve("missing"), false);
    }

    private JsonNode findByName(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        return null;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || Files.notExists(path)) return;

        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    deleteRecursively(child);
                }
            }
        }

        Files.deleteIfExists(path);
    }

}