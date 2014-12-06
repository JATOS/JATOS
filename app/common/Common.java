package common;

import play.Play;

public class Common {

	public static final String VERSION = Play.application().configuration()
			.getString("application.version");
	
}
