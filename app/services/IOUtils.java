package services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;

import models.StudyModel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import play.mvc.Http.MultipartFormData.FilePart;
import controllers.publix.ExternalAssets;

public class IOUtils {

	public static final String STUDY_FILE_SUFFIX = "mas";
	public static final String COMPONENT_FILE_SUFFIX = "mac";
	public static final String ZIP_FILE_SUFFIX = "zip";
	public static final String TXT_FILE_SUFFIX = "txt";

/**
	 * Illegal characters or strings in file or directory name '/', '\n', '\r',
	 * '//', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':',
	 * '~', '!', '§', '$', '%', '&' 
	 */
	public static final String REGEX_ILLEGAL_IN_FILENAME = "[\\s\\n\\r\\t\\f\\*\\?\\\"\\\\\0/,`<>|:~!§$%&]";

	private static final int FILENAME_LENGTH = 35;

	public static String readFile(File file) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
			}
			return sb.toString();
		}
	}

	/**
	 * Gets the File object (can be an directory) while preventing a path
	 * traversal attack.
	 */
	public static File getFileSecurely(String path, String filePath)
			throws IOException {
		path = getExistingStudyDirSecurely(path).getAbsolutePath();
		String fullPath = path + File.separator + filePath;
		String pureFilename = (new File(fullPath)).getName();
		String purePath = (new File(fullPath)).getParentFile()
				.getCanonicalPath();
		File file = new File(purePath, pureFilename);
		if (!file.getAbsolutePath().equals(fullPath)) {
			throw new IOException(
					ErrorMessages.couldntGeneratePathToFileOrDir(filePath));
		}
		return file;
	}

	/**
	 * Gets the File object of the study directory while preventing a path
	 * traversal attack and checks if the directory actually exists.
	 */
	public static File getExistingStudyDirSecurely(String fullPath)
			throws IOException {
		String pureFilename = (new File(fullPath)).getName();
		String purePath = (new File(fullPath)).getParentFile()
				.getCanonicalPath();
		File file = new File(purePath, pureFilename);
		if (!file.getAbsolutePath().equals(fullPath)) {
			throw new IOException(
					ErrorMessages.couldntGeneratePathToFileOrDir(fullPath));
		}
		if (file == null || !file.exists() || !file.isDirectory()) {
			throw new IOException(ErrorMessages.studysDirPathIsntDir(fullPath));
		}
		return file;
	}

	/**
	 * Gets the File object while preventing a path traversal attack and checks
	 * whether the file exists and is no directory.
	 */
	public static File getExistingFileSecurely(String path, String filePath)
			throws IOException {
		File file = getFileSecurely(path, filePath);
		if (file == null || !file.exists() || file.isDirectory()) {
			throw new IOException(ErrorMessages.FILE_NOT_EXIST_OR_DIR);
		}
		return file;
	}

	/**
	 * Gets the File object which resides under filePath within the study's
	 * directory.
	 */
	public static File getFileInStudyDir(StudyModel study, String filePath)
			throws IOException {
		String studyPath = generateStudysPath(study);
		File file = getFileSecurely(studyPath, filePath);
		return file;
	}

	/**
	 * Generates a filename from a name and an ID in a specified length and adds
	 * the suffix. If the ID is null it uses the title only. If the suffix is
	 * null it won't have a file suffix.
	 */
	public static String generateFileName(String rawName, Long id, String suffix) {
		String filename = rawName.trim()
				.replaceAll(REGEX_ILLEGAL_IN_FILENAME, "_").toLowerCase();
		filename = StringUtils.left(filename, FILENAME_LENGTH);
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
	 * Generates a study directory path.
	 */
	public static String generateStudysPath(String dirName, Long studyId) {
		return ExternalAssets.STUDIES_ROOT_PATH + File.separator + dirName;
	}

	/**
	 * Generates a study directory path.
	 */
	public static String generateStudysPath(StudyModel study) {
		return ExternalAssets.STUDIES_ROOT_PATH + File.separator
				+ study.getDirName();
	}

	public static void removeStudyDirectory(StudyModel study)
			throws IOException {
		String dirName = study.getDirName();
		File dir = getFileSecurely(ExternalAssets.STUDIES_ROOT_PATH, dirName);
		if (!dir.exists()) {
			return;
		}
		if (!dir.isDirectory()) {
			throw new IOException(ErrorMessages.studysDirPathIsntDir(dir
					.getName()));
		}
		FileUtils.deleteDirectory(dir);
	}

	public static void copyStudyDirectory(StudyModel srcStudy,
			StudyModel destStudy) throws IOException {
		String srcDirName = srcStudy.getDirName();
		File srcDir = getFileSecurely(ExternalAssets.STUDIES_ROOT_PATH,
				srcDirName);
		if (!srcDir.isDirectory()) {
			throw new IOException(ErrorMessages.studysDirPathIsntDir(srcDir
					.getName()));
		}

		String destDirName = destStudy.getDirName();
		File destDir = getFileSecurely(ExternalAssets.STUDIES_ROOT_PATH,
				destDirName);
		// Check if destination dir already exists and if yes add a number as
		// suffix.
		int i = 1;
		while (destDir.exists()) {
			destDirName = destStudy.getDirName() + "_" + i++;
			destDir = getFileSecurely(ExternalAssets.STUDIES_ROOT_PATH,
					destDirName);
		}
		destStudy.setDirName(destDirName);
		FileUtils.copyDirectory(srcDir, destDir);
	}

	public static void moveStudyDirectory(File srcDir, StudyModel study)
			throws IOException {
		File studyDir = new File(generateStudysPath(study));
		if (studyDir.exists()) {
			throw new IOException(
					ErrorMessages.studysDirNotCreatedBecauseExists(studyDir
							.getName()));
		}
		FileUtils.moveDirectory(srcDir, studyDir);
	}

	public static void createStudyDir(String dirName, Long studyId)
			throws IOException {
		File dir = getFileSecurely(ExternalAssets.STUDIES_ROOT_PATH, dirName);
		if (dir.exists()) {
			throw new IOException(
					ErrorMessages.studysDirNotCreatedBecauseExists(dir
							.getName()));
		}
		boolean result = dir.mkdirs();
		if (!result) {
			throw new IOException(ErrorMessages.studysDirNotCreated(dir
					.getName()));
		}
	}

	public static void createStudyDir(StudyModel study) throws IOException {
		createStudyDir(study.getDirName(), study.getId());
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

	public static void moveFileIntoStudyFolder(FilePart filePart,
			StudyModel study) throws IOException {
		File file = filePart.getFile();
		File destPath = getFileInStudyDir(study, filePart.getFilename());
		boolean result = file.renameTo(destPath);
		if (!result) {
			throw new IOException(ErrorMessages.fileNotRenamed(file.getName(),
					destPath.getName()));
		}
	}

	public static void renameStudyDir(String oldDirName, String newDirName,
			Long studyId) throws IOException {
		File oldDir = new File(IOUtils.generateStudysPath(oldDirName, studyId));
		File newDir = new File(IOUtils.generateStudysPath(newDirName, studyId));
		if (oldDir.exists() && oldDirName.equals(newDirName)) {
			return;
		}
		if (newDir.exists()) {
			throw new IOException(
					ErrorMessages.studysDirNotCreatedBecauseExists(newDir
							.getName()));
		}
		if (!oldDir.exists()) {
			createStudyDir(newDirName, studyId);
			return;
		}
		boolean result = oldDir.renameTo(newDir);
		if (!result) {
			throw new IOException(ErrorMessages.studysDirNotRenamed(
					oldDir.getName(), newDir.getName()));
		}
	}

}
