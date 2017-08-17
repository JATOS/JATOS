package services.gui;

import general.common.MessagesStrings;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import java.io.File;
import java.io.IOException;

/**
 * Unmarshalling of an JSON string - abstract class
 * 
 * @author Kristian Lange
 */
public abstract class UploadUnmarshaller<T> {

	private final IOUtils ioUtils;
	protected final JsonUtils jsonUtils;
	
	UploadUnmarshaller(IOUtils ioUtils, JsonUtils jsonUtils) {
		this.ioUtils = ioUtils;
		this.jsonUtils = jsonUtils;
	}
	
	public T unmarshalling(File file) throws IOException {
		T object = null;
		String jsonStr = null;
		try {
			jsonStr = ioUtils.readFile(file);
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
