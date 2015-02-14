package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for messages (success, info, warning, error) destined for JATOS' GUI
 * views. It's not a persistance model.
 * 
 * @author Kristian Lange
 */
public class Messages {

	private List<String> successList;
	private List<String> infoList;
	private List<String> warningList;
	private List<String> errorList;

	public List<String> getSuccessList() {
		if (successList == null) {
			successList = new ArrayList<>();
		}
		return successList;
	}

	public Messages success(String success) {
		if (success != null) {
			this.getSuccessList().add(success);
		}
		return this;
	}

	public List<String> getInfoList() {
		if (infoList == null) {
			infoList = new ArrayList<>();
		}
		return infoList;
	}

	public Messages info(String info) {
		if (info != null) {
			this.getInfoList().add(info);
		}
		return this;
	}

	public List<String> getWarningList() {
		if (warningList == null) {
			warningList = new ArrayList<>();
		}
		return warningList;
	}

	public Messages warning(String warning) {
		if (warning != null) {
			this.getWarningList().add(warning);
		}
		return this;
	}

	public List<String> getErrorList() {
		if (errorList == null) {
			errorList = new ArrayList<>();
		}
		return errorList;
	}

	public Messages error(String error) {
		if (error != null) {
			this.getErrorList().add(error);
		}
		return this;
	}

}
