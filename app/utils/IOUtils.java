package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;

import play.mvc.Results.Chunks;
import play.mvc.Results.StringChunks;
import services.gui.MessagesStrings;

import com.google.inject.Singleton;

import common.Common;
import controllers.publix.StudyAssets;

/**
 * Utility class that handles access to the system's file system.
 * 
 * @author Kristian Lange
 */
@Singleton
public class IOUtils {

	public static final String STUDY_FILE_SUFFIX = "jas";
	public static final String COMPONENT_FILE_SUFFIX = "jac";
	public static final String ZIP_FILE_SUFFIX = "zip";
	public static final String TXT_FILE_SUFFIX = "txt";

/**
	 * Regular expression of illegal characters or strings in file or directory names
	 * '/', '\n', '\r', * '//', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':',
	 * '~', '!', '§', '$', '%', '&' 
	 */
	public static final String REGEX_ILLEGAL_IN_FILENAME = "[\\s\\n\\r\\t\\f\\*\\?\\\"\\\\\0/,`<>|:~!§$%&]";

	private static final int MAX_FILENAME_LENGTH = 100;

	/**
	 * Reads the given file and returns the content as String.
	 */
	public static String readFile(File file) throws IOException {
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
	 * Gets the File object (can be an directory) while preventing a path
	 * traversal attack. path and filePath together build the full path (like
	 * path/filePath). path must be a directory.
	 */
	public static File getFileSecurely(String path, String filePath)
			throws IOException {
		path = getExistingDirSecurely(path).getAbsolutePath();
		String fullPath = path + File.separator + filePath;
		String pureFilename = (new File(fullPath)).getName();
		String purePath = (new File(fullPath)).getParentFile()
				.getCanonicalPath();
		File file = new File(purePath, pureFilename);
		if (!file.getAbsolutePath().equals(fullPath)) {
			throw new IOException(
					MessagesStrings.couldntGeneratePathToFileOrDir(filePath));
		}
		return file;
	}

	/**
	 * Gets the File object of the directory while preventing a path traversal
	 * attack and checks if the directory actually exists.
	 */
	public static File getExistingDirSecurely(String fullPath)
			throws IOException {
		String pureFilename = (new File(fullPath)).getName();
		String purePath = (new File(fullPath)).getParentFile()
				.getCanonicalPath();
		File file = new File(purePath, pureFilename);
		if (!file.getAbsolutePath().equals(fullPath)) {
			throw new IOException(
					MessagesStrings.couldntGeneratePathToFileOrDir(fullPath));
		}
		if (file == null || !file.exists() || !file.isDirectory()) {
			throw new IOException(MessagesStrings.dirPathIsntDir(fullPath));
		}
		return file;
	}

	public static boolean checkStudyAssetsDirExists(String dirName) {
		File studyAssetsDir = new File(generateStudyAssetsPath(dirName));
		return studyAssetsDir.exists();
	}

	/**
	 * Gets the File object while preventing a path traversal attack and checks
	 * whether the file exists and is no directory.
	 */
	public static File getExistingFileSecurely(String path, String filePath)
			throws IOException {
		File file = getFileSecurely(path, filePath);
		if (file == null || !file.exists() || file.isDirectory()) {
			throw new IOException(MessagesStrings.FILE_NOT_EXIST_OR_DIR);
		}
		return file;
	}

	/**
	 * Gets the File object which resides under filePath within the study
	 * assets' directory.
	 */
	public static File getFileInStudyAssetsDir(String dirName, String filePath)
			throws IOException {
		String studyAssetsPath = generateStudyAssetsPath(dirName);
		return getFileSecurely(studyAssetsPath, filePath);
	}

	/**
	 * Gets the study assets with the given directory name.
	 */
	public static File getStudyAssetsDir(String dirName) throws IOException {
		String studyAssetsPath = generateStudyAssetsPath(dirName);
		return getExistingDirSecurely(studyAssetsPath);
	}

	/**
	 * Generates a filename from a name and an ID in a specified length and adds
	 * the suffix. If the ID is null it uses the title only. If the suffix is
	 * null it won't have a file suffix.
	 */
	public static String generateFileName(String rawName, Long id, String suffix) {
		String filename = rawName.trim()
				.replaceAll(REGEX_ILLEGAL_IN_FILENAME, "_").toLowerCase();
		filename = StringUtils.left(filename, MAX_FILENAME_LENGTH);
		if (id != null) {
			filename = filename.concat("_" + id);
		}
		if (suffix != null) {
			filename = filename.concat("." + suffix);
		}
		return filename;
	}

	/**
	 * Generates a filename from a name in a specified length.
	 */
	public static String generateFileName(String rawName) {
		return generateFileName(rawName, null, null);
	}

	/**
	 * Generates a filename from a name in a specified length and adds the
	 * suffix.
	 */
	public static String generateFileName(String rawName, String suffix) {
		return generateFileName(rawName, null, suffix);
	}

	/**
	 * Generates a study assets directory path.
	 */
	public static String generateStudyAssetsPath(String dirName) {
		return StudyAssets.STUDY_ASSETS_ROOT_PATH + File.separator + dirName;
	}

	/**
	 * Remove study assets' directory if exists.
	 */
	public static void removeStudyAssetsDir(String dirName) throws IOException {
		File dir = getFileSecurely(StudyAssets.STUDY_ASSETS_ROOT_PATH, dirName);
		if (!dir.exists()) {
			return;
		}
		if (!dir.isDirectory()) {
			throw new IOException(MessagesStrings.dirPathIsntDir(dir.getName()));
		}
		FileUtils.deleteDirectory(dir);
	}

	public synchronized static String cloneComponentHtmlFile(
			String studyAssetsDirName, String htmlFileName) throws IOException {
		File htmlFile = getFileInStudyAssetsDir(studyAssetsDirName,
				htmlFileName);
		if (!htmlFile.isFile()) {
			throw new IOException(MessagesStrings.filePathIsntFile(htmlFile
					.getName()));
		}

		File clonedHtmlFile = generateCloneName(htmlFile);
		FileUtils.copyFile(htmlFile, clonedHtmlFile);
		return clonedHtmlFile.getName();
	}

	/**
	 * Copies study assets' directory. Adds suffix '_clone' to the name of the
	 * new assets dir. If a dir with suffix '_clone' already exists it adds '_'
	 * + number instead.
	 */
	public synchronized static String cloneStudyAssetsDirectory(
			String srcDirName) throws IOException {
		File srcDir = getFileSecurely(StudyAssets.STUDY_ASSETS_ROOT_PATH,
				srcDirName);
		if (!srcDir.isDirectory()) {
			throw new IOException(MessagesStrings.dirPathIsntDir(srcDir
					.getName()));
		}

		File destDir = generateCloneName(srcDir);
		FileUtils.copyDirectory(srcDir, destDir);
		return destDir.getName();
	}

	/**
	 * Generates a filename for a clone of the given file. It tries to add the
	 * suffix '_clone'. If the file already exists in the file system it tries
	 * to add numbers starting with 1. It works with files or directories. It
	 * keeps a file extension.
	 */
	private static File generateCloneName(File file) throws IOException {
		String fileExtension = "";
		if (!FilenameUtils.getExtension(file.getName()).isEmpty()) {
			fileExtension = "." + FilenameUtils.getExtension(file.getName());
		}
		String cloneFileName = FilenameUtils.removeExtension(file.getName())
				+ "_clone" + fileExtension;

		File parentDir = file.getParentFile();
		File clonedFile = null;
		int i = 1;
		while (clonedFile == null || clonedFile.exists()) {
			clonedFile = getFileSecurely(parentDir.getAbsolutePath(),
					cloneFileName);
			cloneFileName = FilenameUtils.removeExtension(file.getName()) + "_"
					+ i + fileExtension;
			i++;
		}
		return clonedFile;
	}

	/**
	 * Moves study assets dir into the assets root dir.
	 * 
	 * @param srcDir
	 *            The source dir File can be anywhere in the file system.
	 * @param targetDirName
	 *            Name of the target dir within the assets root dir
	 * @throws IOException
	 */
	public static void moveStudyAssetsDir(File srcDir, String targetDirName)
			throws IOException {
		File targetDir = getFileSecurely(StudyAssets.STUDY_ASSETS_ROOT_PATH,
				targetDirName);
		if (targetDir.exists()) {
			throw new IOException(
					MessagesStrings
							.studyAssetsDirNotCreatedBecauseExists(targetDir
									.getName()));
		}
		FileUtils.moveDirectory(srcDir, targetDir);
	}

	/**
	 * Creates a study assets dir.
	 * 
	 * @param dirName
	 *            Name of the new study assets dir.
	 * @throws IOException
	 */
	public static void createStudyAssetsDir(String dirName) throws IOException {
		File dir = getFileSecurely(StudyAssets.STUDY_ASSETS_ROOT_PATH, dirName);
		if (dir.exists()) {
			throw new IOException(
					MessagesStrings.studyAssetsDirNotCreatedBecauseExists(dir
							.getName()));
		}
		boolean result = dir.mkdirs();
		if (!result) {
			throw new IOException(MessagesStrings.studyAssetsDirNotCreated(dir
					.getName()));
		}
	}

	/**
	 * Returns all files within this directory that have the prefix and the
	 * suffix.
	 */
	public static File[] findFiles(File dir, final String prefix,
			final String suffix) {
		File[] matches = dir.listFiles(new FilenameFilter() {
			public boolean accept(File file, String name) {
				return name.startsWith(prefix) && name.endsWith(suffix);
			}
		});
		return matches;
	}

	/**
	 * Returns all directories within this directory.
	 */
	public static File[] findDirectories(File dir) {
		File[] matches = dir.listFiles(new FilenameFilter() {
			public boolean accept(File file, String name) {
				return file.isDirectory();
			}
		});
		return matches;
	}

	/**
	 * Renames a study assets dir.
	 */
	public static void renameStudyAssetsDir(String oldDirName, String newDirName)
			throws IOException {
		File oldDir = new File(IOUtils.generateStudyAssetsPath(oldDirName));
		File newDir = new File(IOUtils.generateStudyAssetsPath(newDirName));
		if (oldDir.exists() && oldDirName.equals(newDirName)) {
			return;
		}
		if (newDir.exists()) {
			throw new IOException(
					MessagesStrings
							.studyAssetsDirNotCreatedBecauseExists(newDir
									.getName()));
		}
		if (!oldDir.exists()) {
			createStudyAssetsDir(newDirName);
			return;
		}
		boolean result = oldDir.renameTo(newDir);
		if (!result) {
			throw new IOException(MessagesStrings.studyAssetsDirNotRenamed(
					oldDir.getName(), newDir.getName()));
		}
	}

	/**
	 * Reads logs/application.log file in reverse order and returns it as
	 * Chunks<String>. It reads maximal to line lineLimit.
	 */
	public static Chunks<String> readApplicationLog(final int lineLimit) {
		Chunks<String> chunks = new StringChunks() {
			@Override
			public void onReady(play.mvc.Results.Chunks.Out<String> out) {
				File logFile = new File(Common.BASEPATH
						+ "/logs/application.log");
				try (ReversedLinesFileReader reader = new ReversedLinesFileReader(
						logFile)) {
					String content = reader.readLine();
					int lineNumber = 1;
					while (content != null && lineNumber <= lineLimit) {
						out.write(content);
						out.write(System.lineSeparator());
						content = reader.readLine();
						lineNumber++;
					}
				} catch (IOException e) {
					out.write(MessagesStrings.COULDNT_OPEN_LOG);
				} finally {
					out.close();
				}
			}
		};
		return chunks;
	}

}
