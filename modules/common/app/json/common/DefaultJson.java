package json.common;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
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

    private final JsonMapper jsonMapper;

    private final JsonMapper jsonMapperForApi;

    private final ObjectWriter ioWriter;
    private final ObjectWriter publixWriter;
    private final ObjectWriter apiWriter;

    public DefaultJson() {
        // Register Jackson's Hibernate module so Jackson can handle Hibernate proxies and lazy collections.
        // FORCE_LAZY_LOADING is disabled, so serialization will not trigger database loading of uninitialized
        // lazy associations. Uninitialized lazy values are serialized as null / not expanded instead of causing
        // Jackson to traverse Hibernate internals or potentially throwing LazyInitializationException.
        Hibernate5JakartaModule h5Module = new Hibernate5JakartaModule();
        h5Module.disable(Hibernate5JakartaModule.Feature.FORCE_LAZY_LOADING);

        jsonMapper = JsonMapper.builder()
                // Never include source JSON content in exception locations (prevents leaking payload snippets)
                .disable(Feature.INCLUDE_SOURCE_IN_LOCATION)
                .addModule(h5Module)
                .build();
        jsonMapper.setTimeZone(TimeZone.getDefault());

        ioWriter = jsonMapper.writerWithView(JsonForIO.class);
        publixWriter = jsonMapper.writerWithView(JsonForPublix.class);

        jsonMapperForApi = JsonMapper.builder()
                // Never include source JSON content in exception locations (prevents leaking payload snippets)
                .disable(Feature.INCLUDE_SOURCE_IN_LOCATION)
                // Strictly only includes fields annotated with @JsonView(JsonForApi.class)
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .addModule(h5Module)
                .build();
        jsonMapper.setTimeZone(TimeZone.getDefault());

        apiWriter = jsonMapperForApi.writerWithView(JsonForApi.class);
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
        return jsonMapper;
    }

    public <T> T jsonNodeAsObj(JsonNode node, Class<T> clazz) {
        try {
            return jsonMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public JsonNode jsonAsJsonNode(String json) {
        try {
            return jsonMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public String objAsJson(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
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
        return jsonMapper.valueToTree(obj);
    }

    /**
     * Java Object to ObjectNode
     */
    public ObjectNode objAsObjectNode(Object obj) {
        JsonNode node = jsonMapper.valueToTree(obj);
        if (!node.isObject()) {
            throw new JatosException("Expected JSON object, got: " + node.getNodeType(), ApiEnvelope.ErrorCode.INVALID_JSON);
        }
        return (ObjectNode) node;
    }

    /**
     * Serializing an Object into an JSON string. It only considers fields that are annotated with 'JsonForPublix'.
     */
    public String asJsonForPublix(Object obj) {
        try {
            return publixWriter.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    /**
     * Reads the given object into a JsonNode while using the JsonForIO view.
     */
    public JsonNode asJsonForIO(Object obj) {
        try {
            // Unnecessary conversion into a temporary string. Better solution with ObjectWriter.writeValueAsTree
            // when available in later Jackson versions
            return jsonMapper.readTree(ioWriter.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public JsonNode asJsonForApi(Object obj) {
        try {
            // Unnecessary conversion into a temporary string. Better solution with ObjectWriter.writeValueAsTree
            // when available in later Jackson versions
            return jsonMapperForApi.readTree(apiWriter.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

}
