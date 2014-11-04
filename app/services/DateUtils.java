package services;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class DateUtils {

	public static final String DATE_FORMAT_UI = "yyyy-MM-dd HH:mm:ss";
	public static final String DATE_FORMAT_JSON = "yyyy-MM-dd,HH:mm:ss";
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			DATE_FORMAT_UI);

	public static String getPrettyDate(Timestamp timestamp) {
		return DATE_FORMAT.format(timestamp);
	}

}
