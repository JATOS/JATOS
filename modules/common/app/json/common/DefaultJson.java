package json.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import exceptions.common.JatosException;
import general.common.ApiEnvelope;
import play.libs.Json;

import javax.inject.Singleton;
import java.util.TimeZone;

/**
 * Custom Jackson JSON object mapper. Can be used via Json.mapper().
 */
@Singleton
public class DefaultJson {

    private final ObjectMapper mapper;

    public DefaultJson() {
        this.mapper = new ObjectMapper();

        // Never include source JSON content in exception locations (prevents leaking payload snippets)
        mapper.getFactory().disable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);

        // Add the module jackson-datatype-hibernate
        // https://github.com/FasterXML/jackson-datatype-hibernate
        // Hibernate uses lazy loading by default for entity associations. Serialization with Jackson would fail with
        // a LazyInitializationException if the association is not initialized. The FORCE_LAZY_LOADING feature forces
        // the module to load the data from the database before serializing it.
        // todo still necessary? performance?
        Hibernate5Module h5Module = new Hibernate5Module();
        h5Module.disable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
        mapper.registerModule(h5Module);

        // Use the default timezone
        mapper.setTimeZone(TimeZone.getDefault());
    }

    /**
     * Helper class for selectively serializing an Object to JSON. Only fields that are annotated with this class will
     * be serialized. The intended use is in the publix module (used for running a study).
     */
    public static class JsonForPublix {
    }

    /**
     * Helper class for selectively serializing an Object to JSON. Only fields that are annotated with this class will
     * be serialized. Intended use: import/export between different instances of JATOS.
     */
    public static class JsonForIO {
    }

    /**
     * Helper class for selectively serializing an Object to JSON. Only fields that are annotated with this class will
     * be serialized. Intended use: API.
     */
    public static class JsonForApi {
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public <T> T jsonNodeAsObj(JsonNode node, Class<T> clazz) {
        try {
            return mapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public JsonNode jsonAsJsonNode(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public String objAsJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    /**
     * Checks whether the given string is a valid JSON string. An empty string, "hello", 123, true, null are all
     * accepted as valid JSON.
     */
    public static boolean isValid(String json) {
        if (json == null) return false;
        try {
            Json.mapper().readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Java Object to JsonNode
     */
    public JsonNode objAsJsonNode(Object obj) {
        return mapper.valueToTree(obj);
    }

    /**
     * Java Object to ObjectNode
     */
    public ObjectNode objAsObjectNode(Object obj) {
        JsonNode node = mapper.valueToTree(obj);
        if (!node.isObject()) {
            throw new JatosException("Expected JSON object, got: " + node.getNodeType(), ApiEnvelope.ErrorCode.INVALID_JSON);
        }
        return (ObjectNode) node;
    }

    /**
     * Marshalling an Object into an JSON string. It only considers fields that are annotated with 'JsonForPublix'.
     */
    public String asJsonForPublix(Object obj) {
        try {
            ObjectWriter objectWriter = mapper.writerWithView(JsonForPublix.class);
            return objectWriter.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    /**
     * Reads the given object into a JsonNode while using the JsonForIO view.
     */
    public JsonNode asJsonForIO(Object obj) {
        try {
            // Unnecessary conversion into a temporary string - better solution?
            String tmpStr = mapper.writerWithView(JsonForIO.class)
                    .writeValueAsString(obj);
            return mapper.readTree(tmpStr);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public JsonNode asJsonWithStrictViewInclusion(Object obj) {
        // Unnecessary conversion into a temporary string. Better solution with ObjectWriter.writeValueAsTree
        // when available in later Jackson versions
        try {
            String tmpStr = mapper
                    .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    .writerWithView(JsonForApi.class)
                    .writeValueAsString(obj);
            return mapper.readTree(tmpStr);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

}
