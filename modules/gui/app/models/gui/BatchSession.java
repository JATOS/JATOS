package models.gui;

import java.util.ArrayList;
import java.util.List;

import general.common.MessagesStrings;
import play.data.validation.ValidationError;
import utils.common.JsonUtils;

/**
 * @author Kristian Lange (2017)
 */
public class BatchSession {

	public static final String VERSION = "version";
	public static final String DATA = "data";

	private Long version;

	private String data;

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (data != null
				&& !JsonUtils.isValidJSON(data)) {
			errorList.add(new ValidationError(DATA,
					MessagesStrings.INVALID_JSON_FORMAT));
		}
		return errorList.isEmpty() ? null : errorList;
	}

}
