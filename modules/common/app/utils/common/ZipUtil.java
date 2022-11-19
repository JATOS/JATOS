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

import static java.nio.charset.StandardCharsets.*;

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
        destDir = destDir.toPath().normalize().toFile(); // normalize to prevent path traversal attacks
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
     * Generates a zip archive file and writes a list of files into it
     */
    static public void zipFiles(List<Path> filesToZip, File zipFile) throws IOException {
        BufferedOutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(zipFile.toPath()));
        ZipOutputStream out = new ZipOutputStream(fileOutputStream, UTF_8);

        for (Path file : filesToZip) {
            if (Files.exists(file)) addToZip(out, file.getFileName(), file);
        }

        out.flush();
        out.close();
    }

    /**
     * Add a path (can be a file or a directory) to a ZipOutputStream under path zipRoot in the zip
     */
    public static void addToZip(final ZipOutputStream out, final Path zipRoot, final Path file) throws IOException {
        if (Files.isDirectory(file)) {
            addDirToZip(out, zipRoot, file);
        } else {
            addFileToZip(out, Paths.get(""), file);
        }
    }

    /**
     * Writes a directory to the ZipOutputStream walking recursively through the file system.
     */
    public static void addDirToZip(ZipOutputStream out, Path zipRoot, Path file) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
            for (Path child : stream) {
                Path entry = buildPath(zipRoot, child.getFileName());
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

    /**
     * Writes the file using its filename into the ZipOutputStream under path zipRoot in the zip package
     */
    static public void addFileToZip(ZipOutputStream out, Path zipRoot, Path file) throws IOException {
        Path entry = buildPath(zipRoot, file.getFileName());
        out.putNextEntry(new ZipEntry(entry.toString().replace("\\", ZIP_FILE_SEPARATOR)));
        Files.copy(file, out);
        out.closeEntry();
    }

    /**
     * Writes the file into the ZipOutputStream under the path '/zipRoot/zipFilename'
     */
    static public void addFileToZip(ZipOutputStream out, Path zipRoot, Path zipFilename, Path file) throws IOException {
        Path entry = buildPath(zipRoot, zipFilename);
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

    /**
     * Writes the given data as a file to the zip stream using pathInZip as the path in the zip package
     */
    public static void addDataToZip(ZipOutputStream zipOut, String data, String pathInZip) throws IOException {
        if (data != null) {
            ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
            ZipEntry zipEntry = new ZipEntry(pathInZip.replace("\\", ZIP_FILE_SEPARATOR));
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = bais.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            bais.close();
        }
    }

}
