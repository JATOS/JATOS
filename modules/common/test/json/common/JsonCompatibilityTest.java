package json.common;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import play.libs.Json;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

/** v3.11.3 used Json.newDefaultMapper(), with the server timezone, for these scalar values. */
public class JsonCompatibilityTest {
    public static class Payload {
        @JsonView(DefaultJson.JsonForApi.class) public Timestamp date = Timestamp.valueOf("2024-06-15 12:34:56.123");
        @JsonView(DefaultJson.JsonForApi.class) public Long id = 9007199254740993L;
        @JsonView(DefaultJson.JsonForApi.class) public BigDecimal decimal = new BigDecimal("123456789.0123456789");
        @JsonView(DefaultJson.JsonForApi.class) public String unicode = "Grüße 日本語 😀\n\"quoted\"";
        @JsonView(DefaultJson.JsonForApi.class) public String missing = null;
    }

    @Test public void defaultScalarRepresentationMatchesLegacyPlayMapper() throws Exception {
        ObjectMapper legacy = Json.newDefaultMapper().setTimeZone(TimeZone.getDefault());
        Payload payload = new Payload();
        // Compare wire JSON, not intermediate Jackson node implementations.
        JsonNode expected = legacy.readTree(legacy.writeValueAsString(payload));
        DefaultJson current = new DefaultJson();
        assertEquals(expected, legacy.readTree(current.objAsJson(payload)));
    }

    @Test public void apiScalarRepresentationMatchesLegacyPlayMapper() throws Exception {
        ObjectMapper legacy = Json.newDefaultMapper().setTimeZone(TimeZone.getDefault());
        Payload payload = new Payload();
        JsonNode expected = legacy.readTree(legacy.writeValueAsString(payload));
        assertEquals(expected, legacy.readTree(new DefaultJson().asJsonForApi(payload).toString()));
    }
}
