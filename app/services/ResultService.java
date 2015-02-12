package services;

import java.util.ArrayList;
import java.util.List;

import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.JatosGuiException;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;

	@Inject
	ResultService(JatosGuiExceptionThrower jatosGuiExceptionThrower) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
	}

	/**
	 * Parses a String with result IDs and returns them in a List<Long>. Throws
	 * a JatosGuiException if an ID is not a number or if the original String
	 * doesn't contain any ID.
	 */
	public List<Long> extractResultIds(String resultIds)
			throws JatosGuiException {
		String[] resultIdStrArray = resultIds.split(",");
		List<Long> resultIdList = new ArrayList<>();
		for (String idStr : resultIdStrArray) {
			try {
				if (idStr.isEmpty()) {
					continue;
				}
				resultIdList.add(Long.parseLong(idStr.trim()));
			} catch (NumberFormatException e) {
				String errorMsg = ErrorMessages.resultNotExist(idStr);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
		}
		if (resultIdList.size() < 1) {
			String errorMsg = ErrorMessages.NO_RESULTS_SELECTED;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		return resultIdList;
	}

}
