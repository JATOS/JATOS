package utils.common;

import org.apache.commons.io.FileUtils;

import javax.inject.Singleton;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
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

    static public void zipFiles(List<Path> filesToZip, File zipFile) throws IOException {
        BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(zipFile));
        ZipOutputStream out = new ZipOutputStream(fileOutputStream);

        for (Path file : filesToZip) {
            if (Files.exists(file)) addToZip(out, file.getFileName(), file);
        }

        out.flush();
        out.close();
    }

    private static void addToZip(final ZipOutputStream out, final Path root, final Path file) throws IOException {
        if (Files.isDirectory(file)) {
            addDirToZip(out, root, file);
        } else {
            addFileToZip(out, Paths.get(""), file);
        }
    }

    private static void addDirToZip(ZipOutputStream out, Path root, Path file) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
            for (Path child : stream) {
                Path entry = buildPath(root, child.getFileName());
                if (Files.isDirectory(child)) {
                    addToZip(out, entry, child);
                } else {
                    out.putNextEntry(new ZipEntry(entry.toString().replace("\\", ZIP_FILE_SEPARATOR)));
                    Files.copy(child, out);
                    out.closeEntry();
                }
            }
        }
    }

    static private void addFileToZip(ZipOutputStream out, Path root, Path file) throws IOException {
        Path entry = buildPath(root, file.getFileName());
        out.putNextEntry(new ZipEntry(entry.toString().replace("\\", ZIP_FILE_SEPARATOR)));
        Files.copy(file, out);
        out.closeEntry();
    }

    private static Path buildPath(final Path root, final Path child) {
        if (root == null) {
            return child;
        } else {
            return Paths.get(root.toString(), child.toString());
        }
    }

}
