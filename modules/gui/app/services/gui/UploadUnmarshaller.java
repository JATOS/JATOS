package services.gui;

import general.common.MessagesStrings;

import java.io.File;
import java.io.IOException;

import utils.common.IOUtils;

/**
 * Unmarshalling of an JSON string without throwing an exception. Instead error
 * message and Exception are stored within the instance.
 * 
 * @author Kristian Lange
 */
public abstract class UploadUnmarshaller<T> {

	protected String errorMsg;
	protected Exception exception;

	public String getErrorMsg() {
		return errorMsg;
	}

	public Exception getException() {
		return exception;
	}

	public T unmarshalling(File file) throws IOException {
		T object = null;
		String jsonStr = null;
		try {
			// Don't unmarshall file directly so we can create error
			// messages.
			jsonStr = IOUtils.readFile(file);
		} catch (IOException e) {
			throw new IOException(MessagesStrings.COULDNT_READ_FILE, e);
		}
		try {
			object = concreteUnmarshaling(jsonStr);
		} catch (IOException e) {
			throw new IOException(MessagesStrings.COULDNT_READ_JSON, e);
		}
		return object;
	}

	/**
	 * Accepts an JSON String and turns the data object within this JSON String
	 * into an object of the type T.
	 */
	protected abstract T concreteUnmarshaling(String jsonStr)
			throws IOException;

}
