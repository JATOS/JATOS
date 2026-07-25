package json.common;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.JatosException;
import messaging.common.Messages;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the JsonUtils class.
 */
public class DefaultJsonTest {

    private final DefaultJson defaultJson = new DefaultJson();

    @SuppressWarnings("unused")
    private static class TestJsonViewsObject {

        @JsonView(DefaultJson.JsonForPublix.class)
        public String publixField = "publix";

        @JsonView(DefaultJson.JsonForIO.class)
        public String ioField = "io";

        @JsonView(DefaultJson.JsonForApi.class)
        public String apiField = "api";

        public String defaultField = "default";

    }

    private static class TestObject {

        public String name;
        public int count;

    }

    @Test
    public void testIsValid() {
        // Test with valid JSON
        assertTrue(DefaultJson.isValid("{\"key\":\"value\"}"));
        assertTrue(DefaultJson.isValid("[1,2,3]"));
        assertTrue(DefaultJson.isValid("{}"));
        assertTrue(DefaultJson.isValid("[]"));

        // Test with invalid JSON
        assertFalse(DefaultJson.isValid("{key:value}"));
        assertFalse(DefaultJson.isValid("[1,2,"));
        assertFalse(DefaultJson.isValid("not json"));
        assertFalse(DefaultJson.isValid(null));

        // Test with empty string
        assertTrue(DefaultJson.isValid(""));
    }

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
    public void testObjAsJson() {
        // Test with simple object
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        String json = defaultJson.objAsJson(map);
        assertEquals("{\"key\":\"value\"}", json);

        // Test with array
        String[] array = {"one", "two", "three"};
        json = defaultJson.objAsJson(array);
        assertEquals("[\"one\",\"two\",\"three\"]", json);

        // Test with null
        json = defaultJson.objAsJson(null);
        assertEquals("null", json);
    }

    @Test
    public void testObjAsJsonNode() {
        // Test with simple object
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        JsonNode node = defaultJson.objAsJsonNode(map);
        assertTrue(node.isObject());
        assertEquals("value", node.get("key").asText());

        // Test with array
        String[] array = {"one", "two", "three"};
        node = defaultJson.objAsJsonNode(array);
        assertTrue(node.isArray());
        assertEquals(3, node.size());
        assertEquals("one", node.get(0).asText());

        // Test with null
        node = defaultJson.objAsJsonNode(null);
        assertTrue(node.isNull());
    }

    @Test
    public void testObjAsJso_emptyMessagesSerialization() {
        Messages messages = new Messages();
        String json = defaultJson.objAsJson(messages);
        assertNotNull("JSON should not be null", json);
        assertEquals("{}", json);
    }

    @Test
    public void testJsonAsJsonNode() {
        JsonNode node = defaultJson.jsonAsJsonNode("{\"key\":\"value\",\"number\":3}");

        assertTrue(node.isObject());
        assertEquals("value", node.get("key").asText());
        assertEquals(3, node.get("number").asInt());
    }

    @Test(expected = JatosException.class)
    public void testJsonAsJsonNode_invalidJsonThrowsJatosException() {
        defaultJson.jsonAsJsonNode("{key:value}");
    }

    @Test
    public void testJsonNodeAsObj() {
        JsonNode node = defaultJson.jsonAsJsonNode("{\"name\":\"test\",\"count\":5}");

        TestObject obj = defaultJson.jsonNodeAsObj(node, TestObject.class);

        assertEquals("test", obj.name);
        assertEquals(5, obj.count);
    }

    @Test(expected = JatosException.class)
    public void testJsonNodeAsObj_invalidTypeThrowsJatosException() {
        JsonNode node = defaultJson.jsonAsJsonNode("{\"name\":\"test\",\"count\":\"not a number\"}");

        defaultJson.jsonNodeAsObj(node, TestObject.class);
    }

    @Test
    public void testObjAsObjectNode() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");

        JsonNode node = defaultJson.objAsObjectNode(map);

        assertTrue(node.isObject());
        assertEquals("value", node.get("key").asText());
    }

    @Test(expected = JatosException.class)
    public void testObjAsObjectNode_arrayThrowsJatosException() {
        defaultJson.objAsObjectNode(new String[]{"one", "two"});
    }

    @Test(expected = JatosException.class)
    public void testObjAsObjectNode_nullThrowsJatosException() {
        defaultJson.objAsObjectNode(null);
    }

    @Test
    public void testAsJsonForPublix_usesPublixView() {
        String json = defaultJson.asJsonForPublix(new TestJsonViewsObject());
        JsonNode node = defaultJson.jsonAsJsonNode(json);

        assertEquals("publix", node.get("publixField").asText());
        assertFalse(node.has("ioField"));
        assertFalse(node.has("apiField"));
        assertEquals("default", node.get("defaultField").asText());
    }

    @Test
    public void testAsJsonForIO_usesIOView() {
        JsonNode node = defaultJson.asJsonForIO(new TestJsonViewsObject());

        assertFalse(node.has("publixField"));
        assertEquals("io", node.get("ioField").asText());
        assertFalse(node.has("apiField"));
        assertEquals("default", node.get("defaultField").asText());
    }

    @Test
    public void testAsJsonWithStrictViewInclusion_onlyIncludesApiView() {
        JsonNode node = defaultJson.asJsonForApi(new TestJsonViewsObject());

        assertFalse(node.has("publixField"));
        assertFalse(node.has("ioField"));
        assertEquals("api", node.get("apiField").asText());
        assertFalse(node.has("defaultField"));
    }

}