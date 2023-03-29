package utils.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    public static JsonNode get(File base, boolean flatten) throws IOException {
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

    private static Node getNode(File node, File base) throws IOException {
        if (node.isDirectory()) {
            return new Node(node, base, getDirList(node, base));
        } else {
            return new Node(node, base, null);
        }
    }

    private static List<Node> getDirList(File node, File base) throws IOException {
        List<Node> nodeList = new ArrayList<>();
        for (File n : Objects.requireNonNull(node.listFiles())) {
            nodeList.add(getNode(n, base));
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

        public Node(File node, File base, List<Node> content) throws IOException {
            String path = base.toURI().relativize(node.toURI()).getPath();
            BasicFileAttributes attributes = Files.getFileAttributeView(node.toPath(), BasicFileAttributeView.class)
                    .readAttributes();
            this.name = node.getName();
            this.path = path;
            this.type = attributes.isDirectory() ? "directory" : attributes.isRegularFile() ? "file" : "other";
            this.creation = attributes.creationTime().toMillis();
            this.lastModified = attributes.lastModifiedTime().toMillis();
            this.size = attributes.size();
            this.checksum = attributes.isRegularFile() ? HashUtils.getChecksum(node) : null;
            this.content = content;
        }
    }

}