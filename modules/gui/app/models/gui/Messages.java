package models.gui;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Model for messages (success, info, warning, error) destined for JATOS' GUI
 * views (but never persisted in the DB).
 * 
 * @author Kristian Lange
 */
public class Messages {

	@JsonInclude(Include.NON_NULL)
	private List<String> successList;
	
	@JsonInclude(Include.NON_NULL)
	private List<String> infoList;
	
	@JsonInclude(Include.NON_NULL)
	private List<String> warningList;
	
	@JsonInclude(Include.NON_NULL)
	private List<String> errorList;

	public List<String> getSuccessList() {
		return successList;
	}

	public void success(String success) {
		if (success == null) {
			return;
		}
		if (successList == null) {
			successList = new ArrayList<>();
		}
		getSuccessList().add(success);
	}

	public List<String> getInfoList() {
		return infoList;
	}

	public void info(String info) {
		if (info == null) {
			return;
		}
		if (infoList == null) {
			infoList = new ArrayList<>();
		}
		this.getInfoList().add(info);
	}

	public List<String> getWarningList() {
		return warningList;
	}

	public void warning(String warning) {
		if (warning == null) {
			return;
		}
		if (warningList == null) {
			warningList = new ArrayList<>();
		}
		this.getWarningList().add(warning);
	}

	public List<String> getErrorList() {
		return errorList;
	}

	public void error(String error) {
		if (error == null) {
			return;
		}
		if (errorList == null) {
			errorList = new ArrayList<>();
		}
		this.getErrorList().add(error);
	}

}
