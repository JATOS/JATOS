package utils.common;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

	public static String getHashMDFive(String str)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] strBytes = str.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] hashByte = md.digest(strBytes);

		// Convert the byte to hex format
		StringBuilder sb = new StringBuilder();
		for (byte aHashByte : hashByte) {
			sb.append(Integer.toString((aHashByte & 0xff) + 0x100, 16)
					.substring(1));
		}
		return sb.toString();
	}
	
}
