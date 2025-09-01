package common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import play.libs.Json;
import utils.common.JsonUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the JsonUtils class.
 */
public class JsonUtilsTest {

    @Test
    public void testIsValid() {
        // Test with valid JSON
        assertTrue(JsonUtils.isValid("{\"key\":\"value\"}"));
        assertTrue(JsonUtils.isValid("[1,2,3]"));
        assertTrue(JsonUtils.isValid("{}"));
        assertTrue(JsonUtils.isValid("[]"));

        // Test with invalid JSON
        assertFalse(JsonUtils.isValid("{key:value}"));
        assertFalse(JsonUtils.isValid("[1,2,"));
        assertFalse(JsonUtils.isValid("not json"));
        assertFalse(JsonUtils.isValid(null));

        // Test with empty string
        assertTrue(JsonUtils.isValid(""));
    }

    @Test
    public void testAsStringForDB() {
        // Test with valid JSON
        assertEquals("{\"key\":\"value\"}", JsonUtils.asStringForDB("{\"key\":\"value\"}"));
        assertEquals("[1,2,3]", JsonUtils.asStringForDB("[1,2,3]"));

        // Test with formatted JSON (should be minified)
        String formattedJson = "{\n  \"key\": \"value\"\n}";
        assertEquals("{\"key\":\"value\"}", JsonUtils.asStringForDB(formattedJson));

        // Test with invalid JSON (should return the original string)
        String invalidJson = "{key:value}";
        assertEquals(invalidJson, JsonUtils.asStringForDB(invalidJson));

        // Test with empty string or null
        assertNull(JsonUtils.asStringForDB(""));
        assertNull(JsonUtils.asStringForDB(null));
    }

    @Test
    public void testAsJson() {
        // Test with simple object
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        String json = JsonUtils.asJson(map);
        assertEquals("{\"key\":\"value\"}", json);

        // Test with array
        String[] array = {"one", "two", "three"};
        json = JsonUtils.asJson(array);
        assertEquals("[\"one\",\"two\",\"three\"]", json);

        // Test with null
        json = JsonUtils.asJson(null);
        assertEquals("null", json);
    }

    @Test
    public void testAsJsonNode() {
        // Test with simple object
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        JsonNode node = JsonUtils.asJsonNode(map);
        assertTrue(node.isObject());
        assertEquals("value", node.get("key").asText());

        // Test with array
        String[] array = {"one", "two", "three"};
        node = JsonUtils.asJsonNode(array);
        assertTrue(node.isArray());
        assertEquals(3, node.size());
        assertEquals("one", node.get(0).asText());

        // Test with null
        node = JsonUtils.asJsonNode(null);
        assertTrue(node.isNull());
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
        JsonNode wrappedNode = JsonUtils.wrapAsDataEnvelope(innerNode, fields);

        // Verify the structure
        assertTrue(wrappedNode.isObject());
        assertEquals("value1", wrappedNode.get("field1").asText());
        assertEquals(123, wrappedNode.get("field2").asInt());
        assertTrue(wrappedNode.has("data"));

        // Verify the inner node is preserved
        JsonNode dataNode = wrappedNode.get("data");
        assertEquals("innerValue", dataNode.get("innerKey").asText());
    }

    @Test
    public void testWrapForApi() {
        // Create a simple JsonNode
        ObjectNode innerNode = Json.newObject();
        innerNode.put("innerKey", "innerValue");

        // Wrap for API
        JsonNode wrappedNode = JsonUtils.wrapForApi(innerNode);

        // Verify the structure
        assertTrue(wrappedNode.isObject());
        assertTrue(wrappedNode.has("apiVersion"));
        assertTrue(wrappedNode.has("data"));

        // Verify the inner node is preserved
        JsonNode dataNode = wrappedNode.get("data");
        assertEquals("innerValue", dataNode.get("innerKey").asText());

        // Test with additional fields
        Map<String, Object> fields = new HashMap<>();
        fields.put("field1", "value1");

        wrappedNode = JsonUtils.wrapForApi(innerNode, fields);

        // Verify the structure with additional fields
        assertTrue(wrappedNode.isObject());
        assertTrue(wrappedNode.has("apiVersion"));
        assertEquals("value1", wrappedNode.get("field1").asText());
        assertTrue(wrappedNode.has("data"));
    }
}