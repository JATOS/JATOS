package utils.common;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

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
     * Calculates hash for the given file. Converts the byte array into a String of hexadecimal characters.
     */
    public static String getHash(Path file, String hashFunction) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashFunction);
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                while ((dis.read()) != -1) {
                }

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

    /**
     * Generates a random string that can be used for passwords or tokens
     * https://stackoverflow.com/a/31260788/1278769
     */
    public static String generateSecureRandomString(int length) {
        char[] possibleCharacters = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789").toCharArray();
        return RandomStringUtils.random(length, 0, possibleCharacters.length - 1, false, false, possibleCharacters,
                new SecureRandom());
    }

    /**
     * Uses MD5 to generate a 6 chars long checksum of a string
     */
    public static String getChecksum(String str) {
        return HashUtils.getHashMD5(str).substring(0, 6);
    }

    /**
     * Uses Adler32 to calculate a checksum of a file
     */
    public static long getChecksum(File file) throws IOException {
        byte[] tempBuf = new byte[128];
        FileInputStream is = new FileInputStream(file);
        CheckedInputStream cis = new CheckedInputStream(is, new Adler32());
        //noinspection StatementWithEmptyBody - intentionally empty
        while (cis.read(tempBuf) >= 0) {
        }
        return cis.getChecksum().getValue();
    }

}