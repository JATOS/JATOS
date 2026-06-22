package utils.common;

import exceptions.common.JatosException;
import general.common.ApiEnvelope;
import general.common.ApiEnvelope.ErrorCode;

import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class that does zipping and unzipping.
 */
@Singleton
public class ZipUtil {

    /**
     * File separator must be '/' and NOT the system's FILE.SEPARATOR
     */
    private static final String ZIP_FILE_SEPARATOR = "/";

    /**
     * Unzips the given file. Creates a new directory in the system's temp directory and writes the zip's content in
     * there. The method can handle recursive unzipping of subdirectories.
     */
    public static Path unzip(Path fileToUnzip, Path destDir) {
        try (ZipFile zipFile = new ZipFile(fileToUnzip.toFile())) {
            destDir = destDir.normalize(); // normalize to prevent path traversal attacks
            IOUtils.deleteRecursivelyIfExists(destDir);
            Files.createDirectories(destDir);

            Enumeration<? extends ZipEntry> zipEnumeration = zipFile.entries();
            while (zipEnumeration.hasMoreElements()) {
                ZipEntry zipEntry = zipEnumeration.nextElement();
                String fileName = zipEntry.getName();

                Path file = destDir.resolve(fileName).normalize();
                if (!file.startsWith(destDir)) {
                    throw new IOException("Illegal name: " + fileName);
                }

                if (fileName.endsWith(ZIP_FILE_SEPARATOR)) {
                    Files.createDirectories(file);
                    continue;
                }

                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                    Files.copy(inputStream, file);
                }
            }
            return destDir;
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Generates a zip archive file and writes a list of files into it
     */
    static public void zipFiles(List<Path> filesToZip, Path zipFile) {
        try (BufferedOutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(zipFile));
             ZipOutputStream out = new ZipOutputStream(fileOutputStream, UTF_8)) {

            for (Path file : filesToZip) {
                if (Files.exists(file)) {
                    addToZip(out, file.getFileName(), file);
                }
            }

            out.flush();
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Add a path (can be a file or a directory) to a ZipOutputStream under path zipRoot in the zip
     */
    public static void addToZip(final ZipOutputStream out, final Path zipRoot, final Path file) {
        if (Files.isDirectory(file)) {
            addDirToZip(out, zipRoot, file);
        } else {
            addFileToZip(out, Path.of(""), file);
        }
    }

    /**
     * Writes a directory to the ZipOutputStream walking recursively through the file system.
     */
    public static void addDirToZip(ZipOutputStream out, Path zipRoot, Path file) {
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
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Writes the file using its filename into the ZipOutputStream under path zipRoot in the zip package
     */
    static public void addFileToZip(ZipOutputStream out, Path zipRoot, Path file) {
        try {
            Path entry = buildPath(zipRoot, file.getFileName());
            out.putNextEntry(new ZipEntry(entry.toString().replace("\\", ZIP_FILE_SEPARATOR)));
            Files.copy(file, out);
            out.closeEntry();
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Writes the file into the ZipOutputStream under the path '/zipRoot/zipFilename'
     */
    static public void addFileToZip(ZipOutputStream out, Path zipRoot, Path zipFilename, Path file) {
        try {
            Path entry = buildPath(zipRoot, zipFilename);
            out.putNextEntry(new ZipEntry(entry.toString().replace("\\", ZIP_FILE_SEPARATOR)));
            Files.copy(file, out);
            out.closeEntry();
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    private static Path buildPath(final Path root, final Path child) {
        if (root == null) {
            return child;
        } else {
            return Path.of(root.toString(), child.toString());
        }
    }

    /**
     * Writes the given data as a file to the zip stream using pathInZip as the path in the zip package
     */
    public static void addDataToZip(ZipOutputStream zipOut, String data, String pathInZip) {
        try {
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
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

}
