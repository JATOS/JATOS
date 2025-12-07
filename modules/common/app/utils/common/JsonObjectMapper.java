package utils.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import play.libs.Json;

import java.util.TimeZone;

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
        // Hibernate uses lazy loading by default for entity associations. Serialization with Jackson would fail with
        // a LazyInitializationException if the association is not initialized. The FORCE_LAZY_LOADING feature forces
        // the module to load the data from the database before serializing it.
		Hibernate5Module h5Module = new Hibernate5Module();
		h5Module.disable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
		mapper.registerModule(h5Module);

		// Use the default timezone
		mapper.setTimeZone(TimeZone.getDefault());
		Json.setObjectMapper(mapper);
	}

}
