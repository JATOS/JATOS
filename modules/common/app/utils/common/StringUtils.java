package utils.common;

import com.google.common.base.Strings;

import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

public class StringUtils {

    public static String humanReadableByteCount(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    public static String humanReadableDuration(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    public static String getDateTimeYyyyMMddHHmmss() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    public static Optional<Long> parseLong(String str) {
        try {
            return Optional.of(Long.parseLong(str));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets the number of bytes in UTF-8 the given string has
     */
    public static int getStringSize(String str) {
        return !Strings.isNullOrEmpty(str) ? str.getBytes(StandardCharsets.UTF_8).length : 0;
    }

    public  static String anonymizeUsername(String username) {
        if (username == null || username.isBlank() || username.equals("unknown")) {
            return "unknown";
        }

        int atIndex = username.indexOf('@');
        if (atIndex > 0) {
            String localPart = username.substring(0, atIndex);
            String domain = username.substring(atIndex + 1);

            return anonymizePart(localPart) + "@" + anonymizeDomain(domain);
        }

        return anonymizePart(username);
    }

    public static String anonymizePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        int length = value.length();

        if (length <= 2) {
            return "*".repeat(length);
        }

        if (length <= 5) {
            return value.charAt(0) + "*".repeat(length - 2) + value.charAt(length - 1);
        }

        int visible = Math.min(3, length / 3);
        return value.substring(0, visible)
                + "***"
                + value.substring(length - visible);
    }

    public static String anonymizeDomain(String domain) {
        int dotIndex = domain.lastIndexOf('.');
        if (dotIndex <= 0) {
            return anonymizePart(domain);
        }

        String domainName = domain.substring(0, dotIndex);
        String topLevelDomain = domain.substring(dotIndex);

        return anonymizePart(domainName) + topLevelDomain;
    }

}
