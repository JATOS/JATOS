package messaging.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import play.libs.Json;

import static org.junit.Assert.*;

public class MessagesTest {

    @Test
    public void newMessagesHasNoMessageLists() {
        Messages messages = new Messages();

        assertNull(messages.getSuccessList());
        assertNull(messages.getInfoList());
        assertNull(messages.getWarningList());
        assertNull(messages.getErrorList());
    }

    @Test
    public void successAddsSuccessMessage() {
        Messages messages = new Messages();

        messages.success("Saved successfully");

        assertNotNull(messages.getSuccessList());
        assertEquals(1, messages.getSuccessList().size());
        assertEquals("Saved successfully", messages.getSuccessList().get(0));
        assertNull(messages.getInfoList());
        assertNull(messages.getWarningList());
        assertNull(messages.getErrorList());
    }

    @Test
    public void successAddsMultipleSuccessMessagesInOrder() {
        Messages messages = new Messages();

        messages.success("First success");
        messages.success("Second success");

        assertEquals(2, messages.getSuccessList().size());
        assertEquals("First success", messages.getSuccessList().get(0));
        assertEquals("Second success", messages.getSuccessList().get(1));
    }

    @Test
    public void infoAddsInfoMessage() {
        Messages messages = new Messages();

        messages.info("Some information");

        assertNotNull(messages.getInfoList());
        assertEquals(1, messages.getInfoList().size());
        assertEquals("Some information", messages.getInfoList().get(0));
        assertNull(messages.getSuccessList());
        assertNull(messages.getWarningList());
        assertNull(messages.getErrorList());
    }

    @Test
    public void infoAddsMultipleInfoMessagesInOrder() {
        Messages messages = new Messages();

        messages.info("First info");
        messages.info("Second info");

        assertEquals(2, messages.getInfoList().size());
        assertEquals("First info", messages.getInfoList().get(0));
        assertEquals("Second info", messages.getInfoList().get(1));
    }

    @Test
    public void warningAddsWarningMessage() {
        Messages messages = new Messages();

        messages.warning("Careful");

        assertNotNull(messages.getWarningList());
        assertEquals(1, messages.getWarningList().size());
        assertEquals("Careful", messages.getWarningList().get(0));
        assertNull(messages.getSuccessList());
        assertNull(messages.getInfoList());
        assertNull(messages.getErrorList());
    }

    @Test
    public void warningAddsMultipleWarningMessagesInOrder() {
        Messages messages = new Messages();

        messages.warning("First warning");
        messages.warning("Second warning");

        assertEquals(2, messages.getWarningList().size());
        assertEquals("First warning", messages.getWarningList().get(0));
        assertEquals("Second warning", messages.getWarningList().get(1));
    }

    @Test
    public void errorAddsErrorMessage() {
        Messages messages = new Messages();

        messages.error("Something failed");

        assertNotNull(messages.getErrorList());
        assertEquals(1, messages.getErrorList().size());
        assertEquals("Something failed", messages.getErrorList().get(0));
        assertNull(messages.getSuccessList());
        assertNull(messages.getInfoList());
        assertNull(messages.getWarningList());
    }

    @Test
    public void errorAddsMultipleErrorMessagesInOrder() {
        Messages messages = new Messages();

        messages.error("First error");
        messages.error("Second error");

        assertEquals(2, messages.getErrorList().size());
        assertEquals("First error", messages.getErrorList().get(0));
        assertEquals("Second error", messages.getErrorList().get(1));
    }

    @Test
    public void nullMessagesAreIgnored() {
        Messages messages = new Messages();

        messages.success(null);
        messages.info(null);
        messages.warning(null);
        messages.error(null);

        assertNull(messages.getSuccessList());
        assertNull(messages.getInfoList());
        assertNull(messages.getWarningList());
        assertNull(messages.getErrorList());
    }

    @Test
    public void emptyStringsAreAdded() {
        Messages messages = new Messages();

        messages.success("");
        messages.info("");
        messages.warning("");
        messages.error("");

        assertEquals("", messages.getSuccessList().get(0));
        assertEquals("", messages.getInfoList().get(0));
        assertEquals("", messages.getWarningList().get(0));
        assertEquals("", messages.getErrorList().get(0));
    }

    @Test
    public void differentMessageTypesAreIndependent() {
        Messages messages = new Messages();

        messages.success("Success");
        messages.info("Info");
        messages.warning("Warning");
        messages.error("Error");

        assertEquals(1, messages.getSuccessList().size());
        assertEquals(1, messages.getInfoList().size());
        assertEquals(1, messages.getWarningList().size());
        assertEquals(1, messages.getErrorList().size());

        assertEquals("Success", messages.getSuccessList().get(0));
        assertEquals("Info", messages.getInfoList().get(0));
        assertEquals("Warning", messages.getWarningList().get(0));
        assertEquals("Error", messages.getErrorList().get(0));
    }

    @Test
    public void emptyMessagesSerializesToEmptyJsonObject() {
        Messages messages = new Messages();

        JsonNode json = Json.toJson(messages);

        assertTrue(json.isObject());
        assertEquals(0, json.size());
    }

    @Test
    public void messagesSerializeOnlyNonNullLists() {
        Messages messages = new Messages();

        messages.success("Success");
        messages.error("Error");

        JsonNode json = Json.toJson(messages);

        assertTrue(json.has("successList"));
        assertEquals("Success", json.get("successList").get(0).asText());

        assertFalse(json.has("infoList"));
        assertFalse(json.has("warningList"));

        assertTrue(json.has("errorList"));
        assertEquals("Error", json.get("errorList").get(0).asText());
    }

}