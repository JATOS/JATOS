package general.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import play.libs.Json;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the ApiEnvelope class.
 */
public class ApiEnvelopeTest {

    @Test
    public void wrapJsonNodeAddsApiVersionAndData() {
        ObjectNode data = Json.newObject();
        data.put("id", 1);
        data.put("name", "test");

        JsonNode envelope = ApiEnvelope.wrap(data).asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertEquals(data, envelope.get("data"));
        assertFalse(envelope.has("message"));
        assertFalse(envelope.has("error"));
    }

    @Test
    public void wrapObjectAddsApiVersionAndSerializedData() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        JsonNode envelope = ApiEnvelope.wrap(data).asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertEquals(1, envelope.get("data").get("id").asInt());
        assertEquals("test", envelope.get("data").get("name").asText());
        assertFalse(envelope.has("message"));
        assertFalse(envelope.has("error"));
    }

    @Test
    public void wrapMessageAddsApiVersionAndMessage() {
        JsonNode envelope = ApiEnvelope.wrap("Done successfully").asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertEquals("Done successfully", envelope.get("message").asText());
        assertFalse(envelope.has("data"));
        assertFalse(envelope.has("error"));
    }

    @Test
    public void wrapMessageAndDataAddsBothFields() {
        ObjectNode data = Json.newObject();
        data.put("version", 2);

        JsonNode envelope = ApiEnvelope.wrap("Updated successfully", data).asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertEquals("Updated successfully", envelope.get("message").asText());
        assertEquals(data, envelope.get("data"));
        assertFalse(envelope.has("error"));
    }

    @Test
    public void wrapErrorAddsErrorObjectAndOmitsSuccessFields() {
        JsonNode envelope = ApiEnvelope.wrap("Invalid JSON", ApiEnvelope.ErrorCode.INVALID_JSON).asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertTrue(envelope.has("error"));
        assertEquals("INVALID_JSON", envelope.get("error").get("code").asText());
        assertEquals("Invalid JSON", envelope.get("error").get("message").asText());
        assertFalse(envelope.has("message"));
        assertFalse(envelope.has("data"));
    }

    @Test
    public void emptyEnvelopeOnlyContainsApiVersion() {
        JsonNode envelope = new ApiEnvelope().asJsonNode();

        assertEquals(Common.getJatosApiVersion(), envelope.get("apiVersion").asText());
        assertEquals(1, envelope.size());
        assertFalse(envelope.has("message"));
        assertFalse(envelope.has("data"));
        assertFalse(envelope.has("error"));
    }

    @Test
    public void asJsValueReturnsEquivalentJson() {
        JsonNode jsonNode = ApiEnvelope.wrap("Done successfully").asJsonNode();
        String jsValue = ApiEnvelope.wrap("Done successfully").asJsValue().toString();

        assertEquals(jsonNode.toString(), jsValue);
    }

}