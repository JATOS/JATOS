package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exceptions.common.BadRequestException;
import json.common.DefaultJson;
import models.common.Study;
import org.junit.Before;
import org.junit.Test;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;
import play.libs.Json;
import utils.common.IOUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ApiService.
 */
public class ApiServiceTest {

    private IOUtils ioUtils;
    private ApiService apiService;

    @Before
    public void setup() throws Exception {
        ioUtils = mock(IOUtils.class);
        DefaultJson defaultJson = new DefaultJson();
        apiService = new ApiService(ioUtils, defaultJson);
    }

    @Test
    public void validateProps_withNoErrors_doesNotThrow() {
        // Given
        Validatable<List<ValidationError>> props = Collections::emptyList;

        // When / Then
        apiService.validateProps(props);
    }

    @Test
    public void validateProps_withErrors_throwsBadRequestWithFirstError() {
        // Given
        Validatable<List<ValidationError>> props = () ->
                Collections.singletonList(new ValidationError("title", "must not be empty"));

        try {
            // When
            apiService.validateProps(props);
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("Error in field 'title' - must not be empty");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

    @Test
    public void getFieldFromJson_returnsFieldConvertedToRequestedType() {
        // Given
        JsonNode json = Json.parse("{\"name\":\"Study\",\"amount\":3,\"active\":true}");

        // When / Then
        assertThat(apiService.getFieldFromJson(json, "name", String.class)).isEqualTo("Study");
        assertThat(apiService.getFieldFromJson(json, "amount", Integer.class)).isEqualTo(3);
        assertThat(apiService.getFieldFromJson(json, "active", Boolean.class)).isTrue();
    }

    @Test
    public void getFieldFromJson_withMissingField_throwsBadRequest() {
        // Given
        JsonNode json = Json.parse("{\"name\":\"Study\"}");

        try {
            // When
            apiService.getFieldFromJson(json, "active", Boolean.class);
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("Missing active field");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

    @Test
    public void getFieldFromJson_withNullJson_throwsBadRequest() {
        try {
            // When
            apiService.getFieldFromJson(null, "active", Boolean.class);
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("Missing active field");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

    @Test
    public void getFieldFromJson_withWrongType_throwsBadRequest() {
        // Given
        JsonNode json = Json.parse("{\"amount\":\"not-a-number\"}");

        try {
            // When
            apiService.getFieldFromJson(json, "amount", Integer.class);
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("'amount' field must be of type Integer");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

    @Test
    public void getFieldFromJson_withDefaultValue_returnsDefaultIfFieldIsMissing() {
        // Given
        JsonNode json = Json.parse("{\"name\":\"Study\"}");

        // When
        Integer amount = apiService.getFieldFromJson(json, "amount", Integer.class, 5);

        // Then
        assertThat(amount).isEqualTo(5);
    }

    @Test
    public void getFieldFromJson_withDefaultValue_returnsFieldIfPresent() {
        // Given
        JsonNode json = Json.parse("{\"amount\":7}");

        // When
        Integer amount = apiService.getFieldFromJson(json, "amount", Integer.class, 5);

        // Then
        assertThat(amount).isEqualTo(7);
    }

    @Test
    public void getActiveFlagFromJson_readsActiveBoolean() {
        // Given
        JsonNode json = Json.parse("{\"active\":true}");

        // When / Then
        assertThat(apiService.getActiveFlagFromJson(json)).isTrue();
    }

    @Test
    public void normalizeJsonInputField_withObjectField_serializesObjectToString() {
        // Given
        ObjectNode json = (ObjectNode) Json.parse("{\"studyInput\":{\"foo\":\"bar\",\"answer\":42}}");

        // When
        ObjectNode normalized = apiService.normalizeJsonInputField(json, "studyInput");

        // Then
        assertThat(normalized.get("studyInput").isTextual()).isTrue();
        assertThat(normalized.get("studyInput").asText()).isEqualTo("{\"foo\":\"bar\",\"answer\":42}");
    }

    @Test
    public void normalizeJsonInputField_withArrayField_serializesArrayToString() {
        // Given
        ObjectNode json = (ObjectNode) Json.parse("{\"componentInput\":[1,2,3]}");

        // When
        ObjectNode normalized = apiService.normalizeJsonInputField(json, "componentInput");

        // Then
        assertThat(normalized.get("componentInput").isTextual()).isTrue();
        assertThat(normalized.get("componentInput").asText()).isEqualTo("[1,2,3]");
    }

    @Test
    public void normalizeJsonInputField_withStringField_keepsStringUnchanged() {
        // Given
        ObjectNode json = (ObjectNode) Json.parse("{\"studyInput\":\"already serialized\"}");

        // When
        ObjectNode normalized = apiService.normalizeJsonInputField(json, "studyInput");

        // Then
        assertThat(normalized.get("studyInput").asText()).isEqualTo("already serialized");
    }

    @Test
    public void normalizeJsonInputField_withMissingField_returnsObjectUnchanged() {
        // Given
        ObjectNode json = (ObjectNode) Json.parse("{\"title\":\"Study\"}");

        // When
        ObjectNode normalized = apiService.normalizeJsonInputField(json, "studyInput");

        // Then
        assertThat(normalized == json).isTrue();
        assertThat(normalized.get("title").asText()).isEqualTo("Study");
    }

    @Test
    public void normalizeJsonInputField_prefersDeprecatedJsonDataField() {
        // Given
        ObjectNode json = (ObjectNode) Json.parse("{\"studyInput\":{\"new\":true},\"jsonData\":{\"old\":true}}");

        // When
        ObjectNode normalized = apiService.normalizeJsonInputField(json, "studyInput");

        // Then
        assertThat(normalized.get("jsonData").isTextual()).isTrue();
        assertThat(normalized.get("jsonData").asText()).isEqualTo("{\"old\":true}");
        assertThat(normalized.get("studyInput").isObject()).isTrue();
    }

    @Test
    public void normalizeJsonInputField_withNonObjectJson_throwsBadRequest() {
        // Given
        JsonNode json = Json.parse("[1,2,3]");

        try {
            // When
            apiService.normalizeJsonInputField(json, "studyInput");
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("Request body is not a JSON object");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

    @Test
    public void getSessionNode_asText_returnsSessionDataAsString() {
        // When
        ObjectNode sessionNode = apiService.getSessionNode("{\"foo\":\"bar\"}", 4L, true);

        // Then
        assertThat(sessionNode.get("version").asLong()).isEqualTo(4L);
        assertThat(sessionNode.get("sessionData").isTextual()).isTrue();
        assertThat(sessionNode.get("sessionData").asText()).isEqualTo("{\"foo\":\"bar\"}");
    }

    @Test
    public void getSessionNode_notAsText_returnsSessionDataAsJson() {
        // When
        ObjectNode sessionNode = apiService.getSessionNode("{\"foo\":\"bar\"}", 4L, false);

        // Then
        assertThat(sessionNode.get("version").asLong()).isEqualTo(4L);
        assertThat(sessionNode.get("sessionData").isObject()).isTrue();
        assertThat(sessionNode.get("sessionData").get("foo").asText()).isEqualTo("bar");
    }

    @Test
    public void getSessionNode_withNullSessionData_usesEmptyObject() {
        // When
        ObjectNode sessionNode = apiService.getSessionNode(null, 1L, false);

        // Then
        assertThat(sessionNode.get("version").asLong()).isEqualTo(1L);
        assertThat(sessionNode.get("sessionData").isObject()).isTrue();
        assertThat(sessionNode.get("sessionData").size()).isEqualTo(0);
    }

    @Test
    public void getAssetsFilePath_withNullFilepath_usesFilenameInAssetsRoot() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        Path expected = Path.of("/tmp/study-assets/file.txt");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "file.txt")).thenReturn(expected);

        // When
        Path actual = apiService.getAssetsFilePath(null, "file.txt", study);

        // Then
        assertThat(actual.equals(expected)).isTrue();
    }

    @Test
    public void getAssetsFilePath_withRootFilepath_usesFilenameInAssetsRoot() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        Path expected = Path.of("/tmp/study-assets/file.txt");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "file.txt")).thenReturn(expected);

        // When
        Path actual = apiService.getAssetsFilePath("/", "file.txt", study);

        // Then
        assertThat(actual.equals(expected)).isTrue();
    }

    @Test
    public void getAssetsFilePath_withDirectoryFilepath_appendsFilename() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        Path expected = Path.of("/tmp/study-assets/sub/file.txt");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "sub/file.txt")).thenReturn(expected);

        // When
        Path actual = apiService.getAssetsFilePath("/sub/", "file.txt", study);

        // Then
        assertThat(actual.equals(expected)).isTrue();
    }

    @Test
    public void getAssetsFilePath_withFileFilepath_ignoresFilename() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        Path expected = Path.of("/tmp/study-assets/sub/renamed.txt");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "sub/renamed.txt")).thenReturn(expected);

        // When
        Path actual = apiService.getAssetsFilePath("sub/renamed.txt", "uploaded.txt", study);

        // Then
        assertThat(actual.equals(expected)).isTrue();
    }

    @Test
    public void getAssetsFilePath_urlDecodesAndTrimsFilepath() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        Path expected = Path.of("/tmp/study-assets/sub dir/file.txt");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "sub dir/file.txt")).thenReturn(expected);

        // When
        Path actual = apiService.getAssetsFilePath(" /sub%20dir/file.txt ", "uploaded.txt", study);

        // Then
        assertThat(actual.equals(expected)).isTrue();
    }

    @Test
    public void getAssetsFilePath_whenIoUtilsRejectsPath_throwsBadRequest() throws Exception {
        // Given
        Study study = new Study();
        study.setDirName("study-assets");
        when(ioUtils.getFileInStudyAssetsDir("study-assets", "../attack.txt"))
                .thenThrow(new IOException("Invalid path"));

        try {
            // When
            apiService.getAssetsFilePath("../attack.txt", "uploaded.txt", study);
        } catch (BadRequestException e) {
            // Then
            assertThat(e.getMessage()).isEqualTo("Invalid path: ../attack.txt");
            return;
        }
        throw new AssertionError("Expected BadRequestException");
    }

}