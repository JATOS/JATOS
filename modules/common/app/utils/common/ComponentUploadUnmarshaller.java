package utils.common;

import general.common.MessagesStrings;

import java.io.IOException;

import javax.inject.Inject;

import models.common.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Unmarshalling of an JSON string to a componet. The study's JSON string can be
 * in different versions of the componet to support older JATOS' versions.
 * 
 * For each unmarshalling a new instance of this unmarshaller has to be created.
 * 
 * @author Kristian Lange 2015
 */
public class ComponentUploadUnmarshaller extends UploadUnmarshaller<Component> {

	private Component component;

	@Inject
	ComponentUploadUnmarshaller(IOUtils ioUtils) {
		super(ioUtils);
	}
	
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
		case 0:
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
		return component;
	}

}
