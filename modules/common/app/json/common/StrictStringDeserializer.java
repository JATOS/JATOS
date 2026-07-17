package json.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Can be used as `@JsonDeserialize(using = StrictStringDeserializer.class)` on String fields. Handles deserialization
 * of JSON strings, returning the text content directly. Throws an error for inputs that are not valid JSON strings.
 */
public class StrictStringDeserializer extends JsonDeserializer<String> {

    public StrictStringDeserializer() {
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();

        if (t == JsonToken.VALUE_STRING) {
            return p.getText();
        }

        if (t == JsonToken.VALUE_NULL) {
            return null;
        }

        ctxt.reportInputMismatch(String.class, "Expected a JSON string");
        return null;
    }
}
