package services.gui;

import general.common.MessagesStrings;

import java.io.IOException;
import java.util.stream.Collectors;

import models.common.Component;
import play.Logger;
import play.data.validation.ValidationError;
import utils.common.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Unmarshalling of an JSON string without throwing an exception. Instead error
 * message and Exception are stored within the instance.
 * 
 * @author Kristian Lange
 */
public class ComponentUploadUnmarshaller extends UploadUnmarshaller<Component> {

	private static final String CLASS_NAME = ComponentUploadUnmarshaller.class
			.getSimpleName();

	private Component component;

	/**
	 * Accepts an JSON String and turns the data object within this JSON String
	 * into an object of type Component. It can handle different versions of the
	 * component model. The version is determined by the version field in the
	 * JSON string. Each supported component version has its own model which is
	 * used for unmarshaling.
	 */
	@Override
	protected Component concreteUnmarshaling(String jsonStr) throws IOException {
		JsonNode node = JsonUtils.OBJECTMAPPER.readTree(jsonStr).findValue(
				JsonUtils.VERSION);
		int version = node.asInt();
		if (version > Component.SERIAL_VERSION) {
			throw new IOException(MessagesStrings.TOO_NEW_COMPONENT_VERSION);
		}

		switch (version) {
		case 1:
			// Current version
			node = JsonUtils.OBJECTMAPPER.readTree(jsonStr).findValue(
					JsonUtils.DATA);
			component = JsonUtils.OBJECTMAPPER.treeToValue(node,
					Component.class);
			break;
		default:
			throw new IOException(MessagesStrings.UNSUPPORTED_COMPONENT_VERSION);
		}

		validate(component);
		return component;
	}

	private void validate(Component component) throws IOException {
		if (component.validate() != null) {
			Logger.warn(CLASS_NAME
					+ ".validate: "
					+ component.validate().stream()
							.map(ValidationError::message)
							.collect(Collectors.joining(", ")));
			throw new IOException(MessagesStrings.COMPONENT_INVALID);
		}
	}

}
