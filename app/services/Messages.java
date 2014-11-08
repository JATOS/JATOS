package services;

import java.util.ArrayList;
import java.util.List;

public class Messages {

	private List<String> successList = new ArrayList<>();
	private List<String> infoList = new ArrayList<>();
	private List<String> warningList = new ArrayList<>();
	private List<String> errorList = new ArrayList<>();

	public List<String> getSuccessList() {
		return successList;
	}

	public Messages success(String success) {
		if (success != null) {
			this.successList.add(success);
		}
		return this;
	}

	public List<String> getInfoList() {
		return infoList;
	}

	public Messages info(String info) {
		if (info != null) {
			this.infoList.add(info);
		}
		return this;
	}

	public List<String> getWarningList() {
		return warningList;
	}

	public Messages warning(String warning) {
		if (warning != null) {
			this.warningList.add(warning);
		}
		return this;
	}

	public List<String> getErrorList() {
		return errorList;
	}

	public Messages error(String error) {
		if (error != null) {
			this.errorList.add(error);
		}
		return this;
	}
	
}
