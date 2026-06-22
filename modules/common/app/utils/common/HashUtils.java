package utils.common;

import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;

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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] TOKEN_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

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
     * Calculates hash with the given hash function. Uses ISO_8859_1 charset. Converts the byte array into an String of
     * hexadecimal characters.
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
    public static String getHash(Path file, String hashFunction) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashFunction);
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                //noinspection StatementWithEmptyBody - intentionally empty
                while ((dis.read()) != -1) {
                }

            }
            byte[] hashByte = digest.digest();
            return bytesToHex(hashByte);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
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
     */
    public static String generateSecureRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TOKEN_CHARS[SECURE_RANDOM.nextInt(TOKEN_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * Uses MD5 to generate a checksum of a string with the length specified
     */
    public static String getChecksum(String str, int length) {
        return HashUtils.getHashMD5(str).substring(0, length);
    }

    /**
     * Uses Adler32 to calculate a checksum of a file
     */
    public static long getChecksum(Path file) {
        byte[] tempBuf = new byte[128];
        try (InputStream is = Files.newInputStream(file);
             CheckedInputStream cis = new CheckedInputStream(is, new Adler32())) {
            //noinspection StatementWithEmptyBody - intentionally empty
            while (cis.read(tempBuf) >= 0) {
            }
            return cis.getChecksum().getValue();
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

}