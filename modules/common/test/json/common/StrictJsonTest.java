package json.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import exceptions.common.BadRequestException;
import exceptions.common.JatosException;
import org.junit.Test;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;
import play.libs.Json;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class StrictJsonTest {

    private final StrictJson strictJson = new StrictJson(new ObjectMapper());

    @SuppressWarnings("unused")
    private static class TestObject {

        public String text;
        public Integer number;
        public int primitiveNumber;
        public Boolean active;
        public boolean primitiveActive;

    }

    @SuppressWarnings("unused")
    private static class TestObjectWithStrictString {

        @JsonDeserialize(using = StrictStringDeserializer.class)
        public String text;

    }

    @SuppressWarnings("unused")
    private static class ValidatableTestObject implements Validatable<List<ValidationError>> {

        public String text;
        public Integer number;

        @Override
        public List<ValidationError> validate() {
            return null;
        }

    }

    @Test
    public void jsonNodeAsObjMapsValidJsonNode() {
        JsonNode node = Json.newObject()
                .put("text", "hello")
                .put("number", 7)
                .put("primitiveNumber", 3)
                .put("active", true)
                .put("primitiveActive", false);

        TestObject result = strictJson.jsonNodeAsObj(node, TestObject.class);

        assertEquals("hello", result.text);
        assertEquals(Integer.valueOf(7), result.number);
        assertEquals(3, result.primitiveNumber);
        assertEquals(Boolean.TRUE, result.active);
        assertFalse(result.primitiveActive);
    }

    @Test
    public void jsonNodeAsObjRejectsUnknownProperties() {
        JsonNode node = Json.newObject()
                .put("text", "hello")
                .put("unknown", "value");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof UnrecognizedPropertyException);
    }

    @Test
    public void jsonNodeAsObjRejectsStringToIntegerCoercion() {
        JsonNode node = Json.newObject()
                .put("number", "7");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof InvalidFormatException
                || exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjRejectsEmptyStringToIntegerCoercion() {
        JsonNode node = Json.newObject()
                .put("number", "");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof InvalidFormatException
                || exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjRejectsBooleanToIntegerCoercion() {
        JsonNode node = Json.newObject()
                .put("number", true);

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof MismatchedInputException
                || exception.getCause() instanceof InvalidFormatException);
    }

    @Test
    public void jsonNodeAsObjRejectsFloatToIntegerCoercion() {
        JsonNode node = Json.newObject()
                .put("number", 1.2);

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof InvalidFormatException
                || exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjRejectsIntegerToBooleanCoercion() {
        JsonNode node = Json.newObject()
                .put("active", 1);

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof MismatchedInputException
                || exception.getCause() instanceof InvalidFormatException);
    }

    @Test
    public void jsonNodeAsObjRejectsStringToBooleanCoercion() {
        JsonNode node = Json.newObject()
                .put("active", "true");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof InvalidFormatException
                || exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjRejectsNullForPrimitiveInteger() {
        JsonNode node = Json.newObject()
                .putNull("primitiveNumber");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjRejectsNullForPrimitiveBoolean() {
        JsonNode node = Json.newObject()
                .putNull("primitiveActive");

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObject.class));

        assertTrue(exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void jsonNodeAsObjAllowsNullForWrapperTypesAndStrings() {
        JsonNode node = Json.newObject()
                .putNull("text")
                .putNull("number")
                .putNull("active");

        TestObject result = strictJson.jsonNodeAsObj(node, TestObject.class);

        assertNull(result.text);
        assertNull(result.number);
        assertNull(result.active);
    }

    @Test
    public void jsonNodeAsObjWithStrictStringDeserializerAcceptsString() {
        JsonNode node = Json.newObject()
                .put("text", "hello");

        TestObjectWithStrictString result = strictJson.jsonNodeAsObj(node, TestObjectWithStrictString.class);

        assertEquals("hello", result.text);
    }

    @Test
    public void jsonNodeAsObjWithStrictStringDeserializerAcceptsNull() {
        JsonNode node = Json.newObject()
                .putNull("text");

        TestObjectWithStrictString result = strictJson.jsonNodeAsObj(node, TestObjectWithStrictString.class);

        assertNull(result.text);
    }

    @Test
    public void jsonNodeAsObjWithStrictStringDeserializerRejectsNumber() {
        JsonNode node = Json.newObject()
                .put("text", 123);

        JatosException exception = assertThrows(JatosException.class,
                () -> strictJson.jsonNodeAsObj(node, TestObjectWithStrictString.class));

        assertTrue(exception.getCause() instanceof MismatchedInputException);
    }

    @Test
    public void updateFromJsonUpdatesExistingObject() {
        ValidatableTestObject obj = new ValidatableTestObject();
        obj.text = "old";
        obj.number = 1;

        JsonNode json = Json.newObject()
                .put("text", "new")
                .put("number", 2);

        ValidatableTestObject result = strictJson.updateFromJson(obj, json);

        assertSame(obj, result);
        assertEquals("new", result.text);
        assertEquals(Integer.valueOf(2), result.number);
    }

    @Test
    public void updateFromJsonKeepsExistingValuesForMissingFields() {
        ValidatableTestObject obj = new ValidatableTestObject();
        obj.text = "old";
        obj.number = 1;

        JsonNode json = Json.newObject()
                .put("text", "new");

        ValidatableTestObject result = strictJson.updateFromJson(obj, json);

        assertSame(obj, result);
        assertEquals("new", result.text);
        assertEquals(Integer.valueOf(1), result.number);
    }

    @Test
    public void updateFromJsonThrowsBadRequestExceptionForUnknownProperty() {
        ValidatableTestObject obj = new ValidatableTestObject();

        JsonNode json = Json.newObject()
                .put("unknown", "value");

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> strictJson.updateFromJson(obj, json));

        assertEquals("Error in field 'unknown'", exception.getMessage());
    }

    @Test
    public void updateFromJsonThrowsBadRequestExceptionForInvalidFieldType() {
        ValidatableTestObject obj = new ValidatableTestObject();

        JsonNode json = Json.newObject()
                .put("number", "not a number");

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> strictJson.updateFromJson(obj, json));

        assertEquals("Error in field 'number'", exception.getMessage());
    }

    @Test
    public void firstFieldNameReturnsLastFieldInMappingPath() throws IOException {
        JsonMappingException exception = JsonMappingException.from(
                strictJson.mapper().createParser("{}"),
                "Mapping failed");
        exception.prependPath(new Object(), "outer");
        exception.prependPath(new Object(), "inner");

        Optional<String> fieldName = strictJson.firstFieldName(exception);

        assertTrue(fieldName.isPresent());
        assertEquals("outer", fieldName.get());
    }

    @Test
    public void firstFieldNameReturnsEmptyForExceptionWithoutPath() throws IOException {
        JsonMappingException exception = JsonMappingException.from(
                strictJson.mapper().createParser("{}"),
                "Mapping failed");

        Optional<String> fieldName = strictJson.firstFieldName(exception);

        assertFalse(fieldName.isPresent());
    }

}