package utils.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the file system of a directory to JSON
 * <p>
 * Inspired by https://gist.github.com/shaiful16/863e05c349e34aec6851b6d9f0bc0034
 */
public class DirectoryStructureToJson {

    /**
     * Turns the file system of the given directory to JSON. It does NOT check access rights.
     *
     * @param base    File object of the base directory.
     * @param flatten If true, the returned JSON will be a list of files (not nested, no directories). If false, the
     *                returned JSON will have a nested file structure.
     */
    public static JsonNode get(Path base, boolean flatten) throws IOException {
        Object nodes = flatten ? flatten(getNode(base, base)) : getNode(base, base);
        return Json.mapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .valueToTree(nodes);
    }

    private static List<Node> flatten(Node node) {
        if (node.content == null || node.content.isEmpty()) {
            return Collections.singletonList(node);
        } else {
            List<Node> list = new ArrayList<>();
            for (Node n : node.content) {
                list.addAll(flatten(n));
            }
            return list;
        }
    }

    private static Node getNode(Path node, Path base) throws IOException {
        if (Files.isDirectory(node)) {
            return new Node(node, base, getDirList(node, base));
        } else {
            return new Node(node, base, null);
        }
    }

    private static List<Node> getDirList(Path node, Path base) throws IOException {
        List<Node> nodeList = new ArrayList<>();
        try (var stream = Files.list(node)) {
            for (Path n : (Iterable<Path>) stream::iterator) {
                nodeList.add(getNode(n, base));
            }
        }
        return nodeList;
    }

    public static class Node {
        public String name;
        public String path;
        public String type;
        public long creation;
        public long lastModified;
        public long size;
        public Long checksum;
        public List<Node> content;

        public Node(Path node, Path base, List<Node> content) throws IOException {
            Path path = base.relativize(node);
            BasicFileAttributes attributes = Files.getFileAttributeView(node, BasicFileAttributeView.class)
                    .readAttributes();
            this.name = node.getFileName().toString();
            // Standardize path separators to '/' even on Windows
            this.path = path.toString().replace('\\', '/');
            this.type = attributes.isDirectory() ? "directory" : attributes.isRegularFile() ? "file" : "other";
            this.creation = attributes.creationTime().toMillis();
            this.lastModified = attributes.lastModifiedTime().toMillis();
            this.size = attributes.size();
            this.checksum = attributes.isRegularFile() ? HashUtils.getChecksum(node) : null;
            this.content = content;
        }
    }

}