package json.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import play.libs.Json;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the JsonUtils class.
 */
public class DomainJsonMapperTest {

    private final DomainJsonMapper domainJsonMapper =
            new DomainJsonMapper(new DefaultJson(), null, null, null, null);

    @Test
    public void testAsStringForDB() {
        // Test with valid JSON
        assertEquals("{\"key\":\"value\"}", DomainJsonMapper.asStringForDB("{\"key\":\"value\"}"));
        assertEquals("[1,2,3]", DomainJsonMapper.asStringForDB("[1,2,3]"));

        // Test with formatted JSON (should be minified)
        String formattedJson = "{\n  \"key\": \"value\"\n}";
        assertEquals("{\"key\":\"value\"}", DomainJsonMapper.asStringForDB(formattedJson));

        // Test with invalid JSON (should return the original string)
        String invalidJson = "{key:value}";
        assertEquals(invalidJson, DomainJsonMapper.asStringForDB(invalidJson));

        // Test with empty string or null
        assertNull(DomainJsonMapper.asStringForDB(""));
        assertNull(DomainJsonMapper.asStringForDB(null));
    }

    @Test
    public void testWrapAsDataEnvelope() {
        // Create a simple JsonNode
        ObjectNode innerNode = Json.newObject();
        innerNode.put("innerKey", "innerValue");

        // Create fields to wrap with
        Map<String, Object> fields = new HashMap<>();
        fields.put("field1", "value1");
        fields.put("field2", 123);

        // Wrap the node
        JsonNode wrappedNode = domainJsonMapper.wrapAsDataEnvelope(innerNode, fields);

        // Verify the structure
        assertTrue(wrappedNode.isObject());
        assertEquals("value1", wrappedNode.get("field1").asText());
        assertEquals(123, wrappedNode.get("field2").asInt());
        assertTrue(wrappedNode.has("data"));

        // Verify the inner node is preserved
        JsonNode dataNode = wrappedNode.get("data");
        assertEquals("innerValue", dataNode.get("innerKey").asText());
    }
}