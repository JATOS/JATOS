package utils.common;

import com.google.common.base.Strings;
import general.common.Common;
import general.common.MessagesStrings;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class that handles access to the system's file system.
 *
 * @author Kristian Lange
 */
@Singleton
public class IOUtils {

    /**
     * Regular expression of illegal characters or strings in file or directory names '/', '\n', '\r', * '//', '\t',
     * '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', '~', '!', '§', '$', '%', '&'
     */
    public static final String REGEX_ILLEGAL_IN_FILENAME = "[\\s\\n\\r\\t\\f*?\"\\\\\0/,`<>|:~!§$%&^°]";

    /*
     * No spaces, no nulls
     */
    public static final String REGEX_ILLEGAL_IN_PATH = "^[^\\x00\\s]+$";

    private static final int MAX_FILENAME_LENGTH = 100;

    public static Path tmpDir() {
        return Path.of(Common.getTmpPath());
    }

    public static void copyRecursively(Path source, Path target) throws IOException {
        copyRecursively(source, target, null);
    }

    public static void copyRecursively(Path source, Path target, DirectoryStream.Filter<Path> filter) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destDir = target.resolve(relative);
                Files.createDirectories(destDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (filter != null && !filter.accept(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = source.relativize(file);
                Path destFile = target.resolve(relative);
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteRecursivelyIfExists(Path root) throws IOException {
        if (Files.exists(root)) {
            deleteRecursively(root);
        }
    }

    /**
     * Gets the File object (can be a directory) while preventing a path traversal attack. baseDirPath and filePathStr
     * together build the full path (like path/filePath). baseDirPath must be a directory.
     */
    private Path getFileSecurely(Path baseDirPath, String filePathStr) throws IOException {
        Path filePath = Path.of(filePathStr);
        if (!baseDirPath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotAbsolute(baseDirPath.toString()));
        }
        if (filePath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotRelative(filePath.toString()));
        }

        // Join the two paths together, then normalize so that any ".." elements
        // in the userPath can remove parts of baseDirPath.
        // (e.g. "/foo/bar/baz" + "../attack" -> "/foo/bar/attack")
        final Path resolvedPath = baseDirPath.resolve(filePath).normalize();
        // Make sure the resulting path is still within the required directory.
        // (In the example above, "/foo/bar/attack" is not.)
        if (!resolvedPath.startsWith(baseDirPath)) {
            throw new IOException(MessagesStrings.couldntGeneratePathToFileOrDir(filePath.toString()));
        }
        // Check for spaces and nulls in the path
        if (!checkPath(resolvedPath.toString())) {
            throw new IOException(MessagesStrings.couldntGeneratePathToFileOrDir(filePath.toString()));
        }

        return resolvedPath;
    }

    /**
     * Gets the File object of the directory while preventing a path traversal attack and checks if the directory
     * actually exists.
     */
    private Path getExistingDirSecurely(Path fullPath) throws IOException {
        if (!fullPath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotAbsolute(fullPath.toString()));
        }

        // Normalize so that any ".." gets removed
        // (e.g. "/foo/bar/baz/../attack" -> "/foo/bar/attack")
        // and check that the normalized path is equal to the original one
        if (!fullPath.normalize().equals(fullPath)) {
            throw new IOException(MessagesStrings.couldntGeneratePathToFileOrDir(fullPath.toString()));
        }
        if (!Files.exists(fullPath) || !Files.isDirectory(fullPath)) {
            throw new IOException(MessagesStrings.dirPathIsntDir(fullPath.toString()));
        }
        return fullPath;
    }

    public boolean checkStudyAssetsDirExists(String dirName) {
        return Files.exists(generateStudyAssetsPath(dirName));
    }

    public boolean checkFileInStudyAssetsDirExists(String dirName, String filePath) {
        if (Strings.isNullOrEmpty(filePath)) {
            return false;
        }
        Path studyAssetsPath = generateStudyAssetsPath(dirName);
        try {
            return Files.exists(getFileSecurely(studyAssetsPath, filePath));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the File object while preventing a path traversal attack and checks whether the file exists and is no
     * directory.
     */
    public Path getExistingFileSecurely(String pathStr, String filePathStr) throws IOException {
        Path path = Path.of(pathStr);
        Path file = getFileSecurely(path, filePathStr);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IOException(MessagesStrings.FILE_NOT_EXIST_OR_DIR);
        }
        return file;
    }

    /**
     * Checks filePath for path traversal attacks and existence
     */
    public boolean existsAndSecure(String path, String filePath) {
        try {
            getExistingFileSecurely(path, filePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the File object which resides under filePath within the study assets' directory.
     */
    public Path getFileInStudyAssetsDir(String dirName, String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IOException(MessagesStrings.FILE_MISSING);
        }
        Path studyAssetsPath = generateStudyAssetsPath(dirName);
        return getFileSecurely(studyAssetsPath, filePath);
    }

    /**
     * Gets the File object which resides under filePath within the study assets' directory.
     */
    public Path getExistingFileInStudyAssetsDir(String dirName, String filePath) {
        Path studyAssetsPath = generateStudyAssetsPath(dirName);
        try {
            return getFileSecurely(studyAssetsPath, filePath);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Gets the study assets with the given directory name.
     */
    public Path getStudyAssetsDir(String dirName) throws IOException {
        Path studyAssetsPath = generateStudyAssetsPath(dirName);
        return getExistingDirSecurely(studyAssetsPath);
    }

    /**
     * Generates a filename from a name in a specified length and adds the suffix.
     */
    public String generateFileName(String rawName, String suffix) {
        String filename = rawName.trim().replaceAll(REGEX_ILLEGAL_IN_FILENAME, "_").toLowerCase();
        filename = StringUtils.left(filename, MAX_FILENAME_LENGTH);
        if (suffix != null) {
            filename = filename.concat("." + suffix);
        }
        return filename;
    }

    public static boolean checkFilename(String filename) {
        return !Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_FILENAME).matcher(filename).find();
    }

    public static boolean checkPath(String path) {
        return Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_PATH).matcher(path).find();
    }

    /**
     * Generates a filename from a name in a specified length.
     */
    public String generateFileName(String rawName) {
        return generateFileName(rawName, null);
    }

    /**
     * Generates a study assets directory path.
     */
    public static Path generateStudyAssetsPath(String dirName) {
        return Path.of(Common.getStudyAssetsRootPath(), dirName);
    }

    /**
     * Remove study assets' directory if exists.
     */
    public void removeStudyAssetsDir(String dirName) throws IOException {
        Path studyAssetsRootPath = Path.of(Common.getStudyAssetsRootPath());
        Path dir = getFileSecurely(studyAssetsRootPath, dirName);
        if (!Files.exists(dir)) {
            return;
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException(MessagesStrings.dirPathIsntDir(dir.getFileName().toString()));
        }
        deleteRecursively(dir);
    }

    /**
     * Copies a component's HTML file.
     *
     * @param studyAssetsDirName Name of the study assets
     * @param htmlFilePath       Local file path to the HTML file. The file can be in a subdirectory of the study assets
     *                           directory.
     * @return Name of the new file.
     */
    public String cloneComponentHtmlFile(String studyAssetsDirName, String htmlFilePath)
            throws IOException {
        Path htmlFile = getFileInStudyAssetsDir(studyAssetsDirName, htmlFilePath);
        if (!Files.isRegularFile(htmlFile)) {
            throw new IOException(MessagesStrings.filePathIsntFile(htmlFile.getFileName().toString()));
        }

        Path clonedHtmlFile = generateCloneFile(htmlFile);
        Files.copy(htmlFile, clonedHtmlFile);
        return generateLocalFilePathInStudyAssets(clonedHtmlFile, studyAssetsDirName);
    }

    /**
     * Removes the part from the file's path that is the study assets path. The remaining string is only the local path
     * within the study assets directory.
     */
    private String generateLocalFilePathInStudyAssets(Path localFile, String studyAssetsDirName) {
        Path studyAssetsPath = generateStudyAssetsPath(studyAssetsDirName);
        return studyAssetsPath.relativize(localFile).toString();
    }

    /**
     * Copies study assets' directory. Adds suffix '_clone' to the name of the new assets dir. If a dir with suffix
     * '_clone' already exists, it adds '_' + number instead.
     */
    public String cloneStudyAssetsDirectory(String srcDirName) throws IOException {
        Path studyAssetsRootPath = Path.of(Common.getStudyAssetsRootPath());
        Path srcDir = getFileSecurely(studyAssetsRootPath, srcDirName);
        if (!Files.isDirectory(srcDir)) {
            throw new IOException(MessagesStrings.dirPathIsntDir(srcDir.getFileName().toString()));
        }

        Path destDir = generateCloneFile(srcDir);
        copyRecursively(srcDir, destDir);
        return destDir.getFileName().toString();
    }

    /**
     * Generates a filename for a clone of the given file. It tries to add the suffix '_clone'. If the file already
     * exists in the file system, it tries to add numbers starting with 1. It works with files or directories. It keeps
     * a file extension.
     */
    private Path generateCloneFile(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String baseName = (dot > 0) ? name.substring(0, dot) : name;
        String fileExtension = (dot >= 0 && dot < name.length() - 1) ? "." + name.substring(dot + 1) : "";
        String cloneFileName = baseName + "_clone" + fileExtension;

        Path parentDir = file.getParent();
        Path clonedFile = null;
        int i = 1;
        while (clonedFile == null || Files.exists(clonedFile)) {
            clonedFile = getFileSecurely(parentDir, cloneFileName);
            cloneFileName = baseName + "_" + i + fileExtension;
            i++;
        }
        return clonedFile;
    }

    public String findNonExistingStudyAssetsDirName(String dirName) {
        int i = 2;
        String newDirName = dirName;
        while (checkStudyAssetsDirExists(newDirName)) {
            newDirName = dirName + "_" + i;
            i++;
        }
        return newDirName;
    }

    /**
     * Moves study assets dir into the assets root dir.
     *
     * @param srcDir        The source dir File can be anywhere in the file system.
     * @param targetDirName Name of the target dir within the assets root dir
     */
    public void moveStudyAssetsDir(Path srcDir, String targetDirName) throws IOException {
        Path studyAssetsRootPath = Path.of(Common.getStudyAssetsRootPath());
        Path targetDir = getFileSecurely(studyAssetsRootPath, targetDirName);
        if (Files.exists(targetDir)) {
            throw new IOException(MessagesStrings.studyAssetsDirNotCreatedBecauseExists(targetDir.getFileName().toString()));
        }
        Files.move(srcDir, targetDir);
    }

    /**
     * Creates a study assets dir.
     *
     * @param dirName Name of the new study assets dir.
     */
    public void createStudyAssetsDir(String dirName) throws IOException {
        Path studyAssetsRootPath = Path.of(Common.getStudyAssetsRootPath());
        Path dir = getFileSecurely(studyAssetsRootPath, dirName);
        if (Files.exists(dir)) {
            throw new IOException(MessagesStrings.studyAssetsDirNotCreatedBecauseExists(dirName));
        }
        Files.createDirectories(dir);
    }

    /**
     * Returns all files within this directory that have the prefix and the suffix.
     */
    public Path[] findFiles(Path dir, final String prefix, final String suffix) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .toArray(Path[]::new);
        }
    }

    /**
     * Returns all directories within this directory.
     */
    public Path[] findDirectories(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .toArray(Path[]::new);
        }
    }

    /**
     * Renames a study assets dir.
     */
    public void renameStudyAssetsDir(String oldDirName, String newDirName) throws IOException {
        Path oldDir = generateStudyAssetsPath(oldDirName);
        Path newDir = generateStudyAssetsPath(newDirName);
        if (Files.exists(oldDir) && oldDirName.equals(newDirName)) {
            return;
        }
        if (Files.exists(newDir)) {
            throw new IOException(MessagesStrings.studyAssetsNotRenamedBecauseExists(oldDirName, newDirName));
        }
        if (!Files.exists(oldDir)) {
            createStudyAssetsDir(newDirName);
            return;
        }
        Files.move(oldDir, newDir);
    }

    /**
     * Returns the disk size in Bytes of all files inside the given study assets directory. It does not count the size
     * of directories themselves (e.g., on Linux, each directory takes 4kB).
     */
    public long getStudyAssetsDirSize(String dirName) throws IOException {
        Path path = getStudyAssetsDir(dirName);
        if (!Files.exists(path)) return 0;

        try (Stream<Path> stream = Files.walk(path)) {
            long sum = 0L;
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(p)) {
                    sum += Files.size(p);
                }
            }
            return sum;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Renames a component's HTML file. This file can be in a subdirectory of the study assets directory.
     *
     * @param oldHtmlFilePath The current local file path within the study assets
     * @param newHtmlFilePath The new local file path within the study assets
     * @param studyAssetName  The name (not the path) of the study assets
     */
    public void renameHtmlFile(String oldHtmlFilePath, String newHtmlFilePath, String studyAssetName)
            throws IOException {
        Path oldHtmlFile = getFileInStudyAssetsDir(studyAssetName, oldHtmlFilePath);
        Path newHtmlFile = getFileInStudyAssetsDir(studyAssetName, newHtmlFilePath);

        // If the current HTML file doesn't exist or old and new are equal, do nothing
        if (!Files.exists(oldHtmlFile) || oldHtmlFile.equals(newHtmlFile)) {
            return;
        }

        if (Files.exists(newHtmlFile)) {
            throw new IOException(MessagesStrings.htmlFileNotRenamedBecauseExists(oldHtmlFilePath, newHtmlFilePath));
        }

        Files.move(oldHtmlFile, newHtmlFile);
    }

    /**
     * Path to the result uploads folder where JATOS stores the uploaded result files
     */
    public static Path getResultUploadsDir(Long studyResultId) {
        return Path.of(Common.getResultUploadsPath()).resolve("study-result_" + studyResultId);
    }

    /**
     * Path to the result uploads folder where JATOS stores the uploaded result files
     */
    public static Path getResultUploadsDir(Long studyResultId, Long componentResultId) {
        return getResultUploadsDir(studyResultId).resolve("comp-result_" + componentResultId);
    }

    /**
     * Path to result files in a zip package with '/' as file separator
     */
    public static String getResultsPathForZip(Long studyResultId, Long componentResultId) {
        return "study_result_" + studyResultId + "/" + "comp-result_" + componentResultId;
    }

    /**
     * Path to result files in the file system with '/' as a file separator meant to be used in JSON
     */
    public static String getResultsPathForJson(Long studyResultId, Long componentResultId) {
        return "/" + "study_result_" + studyResultId + "/" + "comp-result_" + componentResultId;
    }

    /**
     * Returns the disk size in Bytes of all uploaded files belonging to the given study result ID. It does not count
     * the size of directories themselves (e.g. on Linux each directory takes 4kB).
     */
    public long getResultUploadDirSize(Long studyResultId) {
        Path path = IOUtils.getResultUploadsDir(studyResultId);
        if (!Files.exists(path)) return 0;
        try (Stream<Path> stream = Files.walk(path)) {
            long size = 0L;
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(p)) {
                    size += Files.size(p);
                }
            }
            return size;
        } catch (IOException e) {
            return 0;
        }
    }

    public Path getResultUploadFileSecurely(Long studyResultId, Long componentResultId, String filename)
            throws IOException {
        Path baseDirPath = getResultUploadsDir(studyResultId, componentResultId);
        Files.createDirectories(baseDirPath);
        return getFileSecurely(baseDirPath, filename);
    }

    public void removeResultUploadsDir(Long studyResultId) throws IOException {
        Path dir = getResultUploadsDir(studyResultId);
        if (Files.isDirectory(dir)) {
            deleteRecursively(dir);
        }
    }

    public void removeResultUploadsDir(Long studyResultId, Long componentResultId) throws IOException {
        Path dir = getResultUploadsDir(studyResultId, componentResultId);
        if (Files.isDirectory(dir)) {
            deleteRecursively(dir);
        }
    }

    public static boolean moveAndDetectOverwrite(Path source, Path target) throws IOException {
        try {
            Files.move(source, target);
            return false;
        } catch (FileAlreadyExistsException e) {
            if (Files.isDirectory(target)) {
                deleteRecursively(target);
            } else {
                Files.deleteIfExists(target);
            }
            Files.move(source, target);
            return true;
        }
    }
}
