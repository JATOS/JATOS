package utils.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    public static final String SHA_256 = "SHA-256";

    public static String getHashMD5(String str) {
        try {
            byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashByte = md.digest(strBytes);
            return bytesToHex(hashByte);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates hash with the given hash function. Uses ISO_8859_1 charset. Converts the byte
     * array into an String of hexadecimal characters.
     */
    public static String getHash(String str, String hashFunction) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashFunction);
            byte[] hashByte = digest.digest(str.getBytes(StandardCharsets.ISO_8859_1));
            return bytesToHex(hashByte);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates hash for the given file. Converts the byte array into an String of hexadecimal characters.
     */
    public static String getHash(Path file, String hashFunction) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashFunction);
            try (InputStream is = Files.newInputStream(file);
                    DigestInputStream dis = new DigestInputStream(is, digest)) {
                while ((dis.read()) != -1) {}

            }
            byte[] hashByte = digest.digest();
            return bytesToHex(hashByte);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] hashByte) {
        StringBuilder sb = new StringBuilder();
        for (byte aHashByte : hashByte) {
            sb.append(Integer.toString((aHashByte & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return sb.toString();
    }

}
