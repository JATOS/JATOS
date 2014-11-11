package services;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

	public static final String DATE_FORMAT_FILE = "yyyyMMddHHmmss";
	public static final SimpleDateFormat DATE_FORMATER_FILE = new SimpleDateFormat(
			DATE_FORMAT_FILE);

	public static String getDateForFile(Date data) {
		return DATE_FORMATER_FILE.format(data);
	}

}
