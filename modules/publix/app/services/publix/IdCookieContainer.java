package services.publix;

import java.util.ArrayList;

import exceptions.publix.BadRequestPublixException;

/**
 * 
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("serial")
public class IdCookieContainer extends ArrayList<IdCookie2> {
	
	public IdCookie2 getWithStudyResultId(long studyResultId) {
		for (IdCookie2 cookie : this) {
			try {
				if (cookie.getStudyResultId() == studyResultId) {
					return cookie;
				}
			} catch (BadRequestPublixException e) {
				// Just ignore
			}
		}
		return null;
	}
	
}
