package services.gui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import exceptions.gui.BadRequestException;
import general.common.MessagesStrings;
import models.gui.ApiEnvelope;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import play.libs.Json;
import play.mvc.Http;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApiService {

    public static void validateProps(Constraints.Validatable<List<ValidationError>> props) throws BadRequestException {
        List<ValidationError> errors = props.validate();
        if (errors != null && !errors.isEmpty()) {
            String msg = "Error in field '" + errors.get(0).key() + "' - " + errors.get(0).message();
            throw new BadRequestException(msg, ApiEnvelope.ErrorCode.VALIDATION_ERROR);
        }
    }

    public static <T> T getFieldFromJson(JsonNode json, String fieldName, Class<T> fieldType) throws BadRequestException {
        if (json == null || json.get(fieldName) == null) {
            throw new BadRequestException("Missing " + fieldName + " field", ApiEnvelope.ErrorCode.INVALID_REQUEST);
        }

        try {
            JsonNode fieldNode = json.get(fieldName);
            return Json.mapper().treeToValue(fieldNode, fieldType);
        } catch (Exception e) {
            String msg = "'" + fieldName + "' field must be of type " + fieldType.getSimpleName();
            throw new BadRequestException(msg, ApiEnvelope.ErrorCode.INVALID_REQUEST);
        }
    }

    public static <T> T getFieldFromJson(JsonNode json, String fieldName, Class<T> fieldType, T defaultValue) throws BadRequestException {
        if (json == null || json.get(fieldName) == null) {
            return defaultValue;
        }
        return getFieldFromJson(json, fieldName, fieldType);
    }

    public static boolean getActiveFlagFromJson(JsonNode json) throws BadRequestException {
        return getFieldFromJson(json, "active", Boolean.class);
    }

    /**
     * Controller method needs to be annotated with @BodyParser.Of(BodyParser.Raw.class) for this method to work
     * properly
     */
    public static JsonNode getJsonFromBody(Http.Request request) throws BadRequestException, JsonProcessingException {
        Http.RawBuffer raw = request.body().asRaw();
        byte[] bytes = raw != null ? raw.asBytes().toArray() : null;
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Request body is empty", ApiEnvelope.ErrorCode.INVALID_REQUEST);
        }
        String body = new String(bytes, UTF_8);
        JsonNode json = Json.mapper().readTree(body);
        return json;
    }

    /**
     * Controller method needs to be annotated with @BodyParser.Of(BodyParser.Raw.class) for this method to work
     * properly
     */
    public static String getSessionDataFromBody(Http.Request request) throws JsonProcessingException {
        Http.RawBuffer raw = request.body().asRaw();
        byte[] bytes = raw != null ? raw.asBytes().toArray() : null;
        String body;
        if (bytes == null || bytes.length == 0) {
            body = "{}";
        } else {
            body = new String(bytes, UTF_8);
        }
        JsonNode json = Json.mapper().readTree(body);
        String sessionData = Json.mapper().writeValueAsString(json); // This validates the JSON
        if (Strings.isNullOrEmpty(sessionData)) sessionData = "{}";
        return sessionData;
    }

    /**
     * Extracts a file from the request body. It can handle different content type headers. It always tries
     * "multipart/form-data". Additionally, it tries all content types in the list "allowedRawTypes".
     */
    public static File extractFile(Http.Request request, String filePartName, List<String> allowedRawTypes)
            throws BadRequestException, IOException {
        String contentType = request.contentType().orElse("").toLowerCase();

        if (contentType.startsWith("multipart/form-data") && filePartName != null) {
            if (request.body().asMultipartFormData() == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            Http.MultipartFormData.FilePart<Object> filePart = request.body().asMultipartFormData().getFile(filePartName);
            if (filePart == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            return (File) filePart.getFile();
        }

        if (allowedRawTypes.stream().anyMatch(contentType::startsWith) || contentType.isEmpty()) {
            Http.RawBuffer raw = request.body().asRaw();
            if (raw == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            // Prefer a temp file if Play stored it on disk (common for larger uploads)
            File rawFile = raw.asFile();
            if (rawFile != null && rawFile.exists() && rawFile.length() > 0) {
                return rawFile;
            }
            // Fallback: raw bytes (small uploads); write to a temp file
            if (raw.asBytes() == null || raw.asBytes().isEmpty()) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            Path tmp = Files.createTempFile("jatos-file-upload-", ".tmp");
            Files.write(tmp, raw.asBytes().toArray());
            return tmp.toFile();
        }
        throw new BadRequestException(
                "Unsupported Content-Type '" + contentType + "'. Use multipart/form-data, " + allowedRawTypes);
    }


}
