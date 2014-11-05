package services;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

	public static final String DATE_FORMAT_UI = "yyyy-MM-dd HH:mm:ss";
	public static final String DATE_FORMAT_JSON = "yyyy-MM-dd,HH:mm:ss";
	public static final String DATE_FORMAT_FILE = "yyyyMMddHHmmss";
	public static final SimpleDateFormat DATE_FORMATER_UI = new SimpleDateFormat(
			DATE_FORMAT_UI);
	public static final SimpleDateFormat DATE_FORMATER_FILE = new SimpleDateFormat(
			DATE_FORMAT_FILE);

	public static String getPrettyDate(Date data) {
		return DATE_FORMATER_UI.format(data);
	}

	public static String getDateForFile(Date data) {
		return DATE_FORMATER_FILE.format(data);
	}

}
