package utils;

import java.io.File;
import java.io.IOException;

import services.ErrorMessages;

/**
 * Unmarshalling of an JSON string without throwing an exception. Instead error
 * message and Exception are stored within the instance.
 * 
 * @author Kristian Lange
 */
public class UploadUnmarshaller {

	private final JsonUtils jsonUtils;

	public UploadUnmarshaller(JsonUtils jsonUtils) {
		this.jsonUtils = jsonUtils;
	}

	private String errorMsg;
	private Exception exception;

	public String getErrorMsg() {
		return errorMsg;
	}

	public Exception getException() {
		return exception;
	}

	public <T> T unmarshalling(File file, Class<T> modelClass) {
		T object = null;
		String jsonStr = null;
		try {
			// Don't unmarshall file directly so we can create error
			// messages.
			jsonStr = IOUtils.readFile(file);
		} catch (IOException e) {
			errorMsg = ErrorMessages.COULDNT_READ_FILE;
			exception = e;
			return null;
		}
		try {
			object = this.jsonUtils.unmarshallingIO(jsonStr, modelClass);
		} catch (IOException e) {
			errorMsg = ErrorMessages.COULDNT_READ_JSON;
			exception = e;
			return null;
		}
		return object;
	}
}