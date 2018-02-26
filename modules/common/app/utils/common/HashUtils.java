package utils.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

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

    public static String getHash(String str, String hashFunction) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashFunction);
            byte[] hashByte = digest.digest(str.getBytes(StandardCharsets.ISO_8859_1));
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
