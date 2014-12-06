package services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

	private static final int BUFFER_SIZE = 4096;

	public static File unzip(File file) throws IOException {
		ZipFile zipFile = new ZipFile(file);
		Enumeration<?> enumeration = zipFile.entries();
		File tempDir = Files.createTempDirectory(
				"JatosImport_" + UUID.randomUUID().toString()).toFile();
		tempDir.deleteOnExit();
		while (enumeration.hasMoreElements()) {
			ZipEntry zipEntry = (ZipEntry) enumeration.nextElement();
			String fileName = zipEntry.getName();
			file = new File(tempDir, fileName);
			if (fileName.endsWith("/")) {
				file.mkdirs();
				continue;
			}

			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}

			InputStream inputStream = zipFile.getInputStream(zipEntry);
			FileOutputStream fileOutputStream = new FileOutputStream(file);
			byte[] bytes = new byte[BUFFER_SIZE];
			int length;
			while ((length = inputStream.read(bytes)) >= 0) {
				fileOutputStream.write(bytes, 0, length);
			}
			inputStream.close();
			fileOutputStream.close();
		}
		zipFile.close();
		return tempDir;
	}

	static public File zipStudy(String studyDirPath, String studyDirNameInZip,
			String studyAsJsonPath) throws IOException {
		File zipFile = File.createTempFile("study", "."
				+ IOUtils.ZIP_FILE_SUFFIX);
		zipFile.deleteOnExit();
		FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
		ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

		// Add the study's directory to zip
		addDirectoryToZip("", studyDirNameInZip, studyDirPath, zipOutputStream);

		// Add study's data as JSON file to zip
		addFileToZip("", studyAsJsonPath, zipOutputStream);

		zipOutputStream.flush();
		zipOutputStream.close();
		return zipFile;
	}

	static private void addDirectoryToZip(String dirPathInZip,
			String dirNameInZip, String dirPath, ZipOutputStream zipOutputStream)
			throws IOException {
		File dir = new File(dirPath);
		if (!dir.isDirectory()) {
			return;
		}
		for (String fileName : dir.list()) {
			String filePathInZip;
			if (dirPathInZip.equals("")) {
				filePathInZip = dirNameInZip;
			} else {
				filePathInZip = dirPathInZip + File.separator + dir.getName();
			}
			addFileToZip(filePathInZip, dir.getAbsolutePath() + File.separator
					+ fileName, zipOutputStream);
		}
	}

	static private void addFileToZip(String filePathInZip, String filePath,
			ZipOutputStream zipOutputStream) throws IOException {

		File file = new File(filePath);
		if (file.isDirectory()) {
			addDirectoryToZip(filePathInZip, "", file.getAbsolutePath(),
					zipOutputStream);
		} else {
			FileInputStream fileInputStream = null;
			try {
				byte[] buffer = new byte[BUFFER_SIZE];
				int length;
				fileInputStream = new FileInputStream(file);
				zipOutputStream.putNextEntry(new ZipEntry(filePathInZip
						+ File.separator + file.getName()));
				while ((length = fileInputStream.read(buffer)) > 0) {
					zipOutputStream.write(buffer, 0, length);
				}
			} finally {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
			}
		}
	}

}
