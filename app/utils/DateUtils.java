package utils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.inject.Singleton;

@Singleton
public class DateUtils {

	public static final String DATE_FORMAT_FILE = "yyyyMMddHHmmss";
	public static final SimpleDateFormat DATE_FORMATER_FILE = new SimpleDateFormat(
			DATE_FORMAT_FILE);

	public static String getDateForFile(Date data) {
		return DATE_FORMATER_FILE.format(data);
	}

	public static String getDurationPretty(Timestamp startDate,
			Timestamp endDate) {
		if (endDate != null) {
			long duration = endDate.getTime() - startDate.getTime();
			long diffSeconds = duration / 1000 % 60;
			long diffMinutes = duration / (60 * 1000) % 60;
			long diffHours = duration / (60 * 60 * 1000) % 24;
			long diffDays = duration / (24 * 60 * 60 * 1000);
			String asStr = String.format("%02d", diffHours) + ":"
					+ String.format("%02d", diffMinutes) + ":"
					+ String.format("%02d", diffSeconds);
			if (diffDays == 0) {
				return asStr;
			} else {
				return diffDays + ":" + asStr;
			}
		}
		return null;
	}
}
