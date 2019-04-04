package utils.common;

import org.apache.commons.io.FileUtils;

import javax.inject.Singleton;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Utility class that does zipping and unzipping.
 *
 * @author Kristian Lange
 */
@Singleton
public class ZipUtil {

    private static final int BUFFER_SIZE = 4096;

    /**
     * File separator must be '/' and NOT the system's FILE.SEPARATOR
     */
    private static final String ZIP_FILE_SEPARATOR = "/";

    /**
     * Unzips the given File. Creates a new directory in the system's temp directory and writes the
     * zip's content in there. The method can handle recursive unzipping of sub-directories.
     */
    public static File unzip(File fileToUnzip, File destDir) throws IOException {
        FileUtils.deleteQuietly(destDir);
        IOUtils.createDir(destDir);
        destDir.deleteOnExit();

        File file;
        ZipFile zipFile = new ZipFile(fileToUnzip);
        Enumeration<?> zipEnumeration = zipFile.entries();
        while (zipEnumeration.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) zipEnumeration.nextElement();
            String fileName = zipEntry.getName();
            file = new File(destDir, fileName);
            if (fileName.endsWith(ZIP_FILE_SEPARATOR)) {
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
        return destDir;
    }

    /**
     * Zips a study. It returns a File object with the name 'study.zip' within
     * the system's temp directory. The zip file will contain the study assets'
     * directory and the study's JSON data (a .jas file).
     */
    static public File zipStudy(String studyAssetsDirPath, String studyAssetsDirNameInZip,
            String studyAsJsonPath) throws IOException {
        File zipFile = File.createTempFile("study", "." + IOUtils.ZIP_FILE_SUFFIX);
        zipFile.deleteOnExit();
        FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

        // Add the study assets' directory to zip
        addDirectoryToZip("", studyAssetsDirNameInZip, studyAssetsDirPath, zipOutputStream);

        // Add study as JSON file to zip
        addFileToZip("", studyAsJsonPath, zipOutputStream);

        zipOutputStream.flush();
        zipOutputStream.close();
        return zipFile;
    }

    static private void addDirectoryToZip(String dirPathInZip, String dirNameInZip, String dirPath,
            ZipOutputStream zipOutputStream) throws IOException {
        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            return;
        }
        for (String fileName : dir.list()) {
            String filePathInZip;
            if (dirPathInZip.equals("")) {
                filePathInZip = dirNameInZip;
            } else {
                filePathInZip = dirPathInZip + ZIP_FILE_SEPARATOR + dir.getName();
            }
            String filePath = dir.getAbsolutePath() + ZIP_FILE_SEPARATOR + fileName;
            addFileToZip(filePathInZip, filePath, zipOutputStream);
        }
    }

    static private void addFileToZip(String filePathInZip, String filePath,
            ZipOutputStream zipOutputStream) throws IOException {

        File file = new File(filePath);
        if (file.isDirectory()) {
            addDirectoryToZip(filePathInZip, "", file.getAbsolutePath(), zipOutputStream);
        } else {
            FileInputStream fileInputStream = null;
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                fileInputStream = new FileInputStream(file);
                zipOutputStream.putNextEntry(
                        new ZipEntry(filePathInZip + ZIP_FILE_SEPARATOR + file.getName()));
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
