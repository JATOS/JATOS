package json.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import exceptions.common.BadRequestException;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * A custom Jackson JSON object mapper with strict validation settings to enforce rigid mapping rules. It is derived
 * from the {@link DefaultJson}.
 *
 * Additionally, it implements a custom deserializer that can be used `@JsonDeserialize` on fields.
 */
@Singleton
public class StrictJson extends JsonDeserializer<String> {

    private final ObjectMapper mapper;

    @Inject
    StrictJson(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy();

        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // Jackson 2.13.x: I couldn't find a way to prevent coercion of scalars to String 12345 -> "12345"
        // that result in a fail and not just in a silent drop of the value. Therefore, the JsonDeserializer was added.
        // Use `@JsonDeserialize(using = StrictJsonMapper.class)` on fields.

        // Avoid silent defaults for primitives (e.g. null -> 0/false)
        this.mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

        // Reject float -> int coercion (e.g. 1.2 into Long/Integer)
        this.mapper.configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false);

        this.mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Object, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Array, CoercionAction.Fail);

        this.mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);

        this.mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Object, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Array, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Can be used as `@JsonDeserialize(using = StrictJsonMapper.class)` on fields. Handles deserialization of JSON
     * strings, returning the text content directly. Throws an error for inputs that are not valid JSON strings.
     */
    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        // This becomes a JsonMappingException with a proper path
        ctxt.reportInputMismatch(String.class, "Expected a JSON string");
        return null; // unreachable
    }

    public <T> T jsonNodeAsObj(JsonNode node, Class<T> clazz) {
        try {
            return mapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new JatosException(e);
        }
    }

    public <T extends Validatable<List<ValidationError>>> T updateFromJson(T obj, JsonNode json) {
        try {
            return mapper.readerForUpdating(obj).readValue(json);
        } catch (JsonMappingException e) {
            String msg = "Error in field '" + firstFieldName(e).orElse("unknown") + "'";
            throw new BadRequestException(msg, ErrorCode.VALIDATION_ERROR);
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    public Optional<String> firstFieldName(JsonMappingException e) {
        List<JsonMappingException.Reference> path = e.getPath();
        if (path == null || path.isEmpty()) return Optional.empty();
        JsonMappingException.Reference last = path.get(path.size() - 1);
        return Optional.ofNullable(last.getFieldName());
    }

}
