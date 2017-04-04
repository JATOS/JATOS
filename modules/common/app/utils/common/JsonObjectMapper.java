package utils.common;

import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

import play.libs.Json;

/**
 * Custom Jackson JSON object mapper. Can be used via Json.mapper().
 * 
 * @author Kristian Lange (2017)
 */
public class JsonObjectMapper {

	public JsonObjectMapper() {
		ObjectMapper mapper = Json.newDefaultMapper();

		// Add the module jackson-datatype-hibernate
		// https://github.com/FasterXML/jackson-datatype-hibernate
		// It allows to disable automatic lazy loading of Hibernate entities.
		Hibernate5Module h5Module = new Hibernate5Module();
		h5Module.disable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
		mapper.registerModule(h5Module);

		// Use the default timezone
		mapper.setTimeZone(TimeZone.getDefault());
		Json.setObjectMapper(mapper);
	}

}
