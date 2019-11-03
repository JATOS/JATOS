package utils.common;

import general.common.Common;
import general.common.MessagesStrings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class that handles access to the system's file system.
 *
 * @author Kristian Lange
 */
@Singleton
public class IOUtils {

    public static final String STUDY_FILE_SUFFIX = "jas";
    public static final String COMPONENT_FILE_SUFFIX = "jac";
    public static final String ZIP_FILE_SUFFIX = "jzip";

    /**
     * Regular expression of illegal characters or strings in file or directory names '/', '\n', '\r', * '//', '\t',
     * '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', '~', '!', '§', '$', '%', '&'
     */
    public static final String REGEX_ILLEGAL_IN_FILENAME = "[\\s\\n\\r\\t\\f*?\"\\\\\0/,`<>|:~!§$%&^°]";

    private static final int MAX_FILENAME_LENGTH = 100;

    public static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"));

    /**
     * Reads the given file and returns the content as String.
     */
    public String readFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String content = reader.readLine();

            while (content != null) {
                sb.append(content);
                sb.append(System.lineSeparator());
                content = reader.readLine();
            }
            return sb.toString();
        }
    }

    /**
     * Gets the File object (can be an directory) while preventing a path traversal attack. path and filePath together
     * build the full path (like path/filePath). path must be a directory.
     */
    private File getFileSecurely(String baseDirPathStr, String filePathStr) throws IOException {
        Path baseDirPath = Paths.get(baseDirPathStr);
        Path filePath = Paths.get(filePathStr);
        if (!baseDirPath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotAbsolute(baseDirPathStr));
        }
        if (filePath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotRelative(filePathStr));
        }

        // Join the two paths together, then normalize so that any ".." elements
        // in the userPath can remove parts of baseDirPath.
        // (e.g. "/foo/bar/baz" + "../attack" -> "/foo/bar/attack")
        final Path resolvedPath = baseDirPath.resolve(filePath).normalize();
        // Make sure the resulting path is still within the required directory.
        // (In the example above, "/foo/bar/attack" is not.)
        if (!resolvedPath.startsWith(baseDirPath)) {
            throw new IOException(MessagesStrings.couldntGeneratePathToFileOrDir(filePathStr));
        }

        return resolvedPath.toFile();
    }

    /**
     * Gets the File object of the directory while preventing a path traversal attack and checks if the directory
     * actually exists.
     */
    private File getExistingDirSecurely(String fullPathStr) throws IOException {
        Path fullPath = Paths.get(fullPathStr);
        if (!fullPath.isAbsolute()) {
            throw new IOException(MessagesStrings.pathNotAbsolute(fullPathStr));
        }

        // Normalize so that any ".." gets removed
        // (e.g. "/foo/bar/baz/../attack" -> "/foo/bar/attack")
        // and check that the normalized path is equal to the original one
        if (!fullPath.normalize().equals(fullPath)) {
            throw new IOException(MessagesStrings.couldntGeneratePathToFileOrDir(fullPathStr));
        }
        File file = fullPath.toFile();
        if (!file.exists() || !file.isDirectory()) {
            throw new IOException(MessagesStrings.dirPathIsntDir(fullPathStr));
        }
        return file;
    }

    public boolean checkStudyAssetsDirExists(String dirName) {
        File studyAssetsDir = new File(generateStudyAssetsPath(dirName));
        return studyAssetsDir.exists();
    }

    public boolean checkFileInStudyAssetsDirExists(String dirName, String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        String studyAssetsPath = generateStudyAssetsPath(dirName);
        try {
            return getFileSecurely(studyAssetsPath, filePath).exists();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the File object while preventing a path traversal attack and checks whether the file exists and is no
     * directory.
     */
    public File getExistingFileSecurely(String path, String filePath) throws IOException {
        File file = getFileSecurely(path, filePath);
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IOException(MessagesStrings.FILE_NOT_EXIST_OR_DIR);
        }
        return file;
    }

    /**
     * Gets the File object which resides under filePath within the study assets' directory.
     */
    public File getFileInStudyAssetsDir(String dirName, String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IOException(MessagesStrings.FILE_MISSING);
        }
        String studyAssetsPath = generateStudyAssetsPath(dirName);
        return getFileSecurely(studyAssetsPath, filePath);
    }

    /**
     * Gets the study assets with the given directory name.
     */
    public File getStudyAssetsDir(String dirName) throws IOException {
        String studyAssetsPath = generateStudyAssetsPath(dirName);
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

    /**
     * Generates a filename from a name in a specified length.
     */
    public String generateFileName(String rawName) {
        return generateFileName(rawName, null);
    }

    /**
     * Generates a study assets directory path.
     */
    public String generateStudyAssetsPath(String dirName) {
        return Common.getStudyAssetsRootPath() + File.separator + dirName;
    }

    /**
     * Remove study assets' directory if exists.
     */
    public void removeStudyAssetsDir(String dirName) throws IOException {
        File dir = getFileSecurely(Common.getStudyAssetsRootPath(), dirName);
        if (!dir.exists()) {
            return;
        }
        if (!dir.isDirectory()) {
            throw new IOException(MessagesStrings.dirPathIsntDir(dir.getName()));
        }
        FileUtils.deleteDirectory(dir);
    }

    /**
     * Copies a component's HTML file.
     *
     * @param studyAssetsDirName Name of the study assets
     * @param htmlFilePath       Local file path to the HTML file. The file can be in a sub-directory of the study
     *                           assets directory.
     * @return Name of the new file.
     */
    public synchronized String cloneComponentHtmlFile(String studyAssetsDirName, String htmlFilePath)
            throws IOException {
        File htmlFile = getFileInStudyAssetsDir(studyAssetsDirName, htmlFilePath);
        if (!htmlFile.isFile()) {
            throw new IOException(MessagesStrings.filePathIsntFile(htmlFile.getName()));
        }

        File clonedHtmlFile = generateCloneFile(htmlFile);
        FileUtils.copyFile(htmlFile, clonedHtmlFile);
        return generateLocalFilePathInStudyAssets(clonedHtmlFile, studyAssetsDirName);
    }

    /**
     * Removes the part from the file's path that is the study assets path. The remaining string is only the local path
     * within the study assets directory.
     */
    private String generateLocalFilePathInStudyAssets(File localFile, String studyAssetsDirName) {
        String studyAssetsPath = generateStudyAssetsPath(studyAssetsDirName) + File.separator;
        return localFile.getAbsolutePath().replace(studyAssetsPath, "");
    }

    /**
     * Copies study assets' directory. Adds suffix '_clone' to the name of the new assets dir. If a dir with suffix
     * '_clone' already exists it adds '_' + number instead.
     */
    public synchronized String cloneStudyAssetsDirectory(String srcDirName) throws IOException {
        File srcDir = getFileSecurely(Common.getStudyAssetsRootPath(), srcDirName);
        if (!srcDir.isDirectory()) {
            throw new IOException(MessagesStrings.dirPathIsntDir(srcDir.getName()));
        }

        File destDir = generateCloneFile(srcDir);
        FileUtils.copyDirectory(srcDir, destDir);
        return destDir.getName();
    }

    /**
     * Generates a filename for a clone of the given file. It tries to add the suffix '_clone'. If the file already
     * exists in the file system it tries to add numbers starting with 1. It works with files or directories. It keeps a
     * file extension.
     */
    private File generateCloneFile(File file) throws IOException {
        String fileExtension = "";
        if (!FilenameUtils.getExtension(file.getName()).isEmpty()) {
            fileExtension = "." + FilenameUtils.getExtension(file.getName());
        }
        String cloneFileName = FilenameUtils.removeExtension(file.getName()) + "_clone" + fileExtension;

        File parentDir = file.getParentFile();
        File clonedFile = null;
        int i = 1;
        while (clonedFile == null || clonedFile.exists()) {
            clonedFile = getFileSecurely(parentDir.getAbsolutePath(), cloneFileName);
            cloneFileName = FilenameUtils.removeExtension(file.getName()) + "_" + i + fileExtension;
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
    public void moveStudyAssetsDir(File srcDir, String targetDirName) throws IOException {
        File targetDir = getFileSecurely(Common.getStudyAssetsRootPath(), targetDirName);
        if (targetDir.exists()) {
            throw new IOException(MessagesStrings.studyAssetsDirNotCreatedBecauseExists(targetDir.getName()));
        }
        FileUtils.moveDirectory(srcDir, targetDir);
    }

    /**
     * Creates a study assets dir.
     *
     * @param dirName Name of the new study assets dir.
     */
    public void createStudyAssetsDir(String dirName) throws IOException {
        File dir = getFileSecurely(Common.getStudyAssetsRootPath(), dirName);
        if (dir.exists()) {
            throw new IOException(MessagesStrings.studyAssetsDirNotCreatedBecauseExists(dir.getName()));
        }
        boolean result = dir.mkdirs();
        if (!result) {
            throw new IOException(MessagesStrings.studyAssetsDirNotCreated(dir.getName()));
        }
    }

    /**
     * Returns all files within this directory that have the prefix and the suffix.
     */
    public File[] findFiles(File dir, final String prefix, final String suffix) {
        return dir.listFiles((file, name) -> name.startsWith(prefix) && name.endsWith(suffix));
    }

    /**
     * Returns all directories within this directory.
     */
    public File[] findDirectories(File dir) {
        return dir.listFiles((file, name) -> file.isDirectory());
    }

    /**
     * Renames a study assets dir.
     */
    public void renameStudyAssetsDir(String oldDirName, String newDirName) throws IOException {
        File oldDir = new File(generateStudyAssetsPath(oldDirName));
        File newDir = new File(generateStudyAssetsPath(newDirName));
        if (oldDir.exists() && oldDirName.equals(newDirName)) {
            return;
        }
        if (newDir.exists()) {
            throw new IOException(
                    MessagesStrings.studyAssetsNotRenamedBecauseExists(oldDir.getName(), newDir.getName()));
        }
        if (!oldDir.exists()) {
            createStudyAssetsDir(newDirName);
            return;
        }
        boolean result = oldDir.renameTo(newDir);
        if (!result) {
            throw new IOException(MessagesStrings.studyAssetsDirNotRenamed(oldDir.getName(), newDir.getName()));
        }
    }

    /**
     * Renames a component's HTML file. This file can be in a sub-directory of the study assets directory.
     *
     * @param oldHtmlFilePath The current local file path within the study assets
     * @param newHtmlFilePath The new local file path within the study assets
     * @param studyAssetName  The name (not the path) of the study assets
     */
    public void renameHtmlFile(String oldHtmlFilePath, String newHtmlFilePath, String studyAssetName)
            throws IOException {
        File oldHtmlFile = getFileInStudyAssetsDir(studyAssetName, oldHtmlFilePath);
        File newHtmlFile = getFileInStudyAssetsDir(studyAssetName, newHtmlFilePath);

        // If the current HTML file doesn't exist or old and new are equal do
        // nothing
        if (!oldHtmlFile.exists() || oldHtmlFile.equals(newHtmlFile)) {
            return;
        }

        if (newHtmlFile.exists()) {
            throw new IOException(MessagesStrings.htmlFileNotRenamedBecauseExists(oldHtmlFilePath, newHtmlFilePath));
        }

        boolean result = oldHtmlFile.renameTo(newHtmlFile);
        if (!result) {
            throw new IOException(MessagesStrings.htmlFileNotRenamed(oldHtmlFilePath, newHtmlFilePath));
        }
    }

    /**
     * Creates the given File as a directory, including necessary and non-existent parent directories. If the file
     * already exists it will be deleted before.
     */
    public static void createDir(File file) throws IOException {
        if (!file.mkdirs()) {
            throw new IOException("Couldn't create directory " + file.getPath());
        }
    }
}
