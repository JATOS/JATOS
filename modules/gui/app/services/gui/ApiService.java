package services.gui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import exceptions.gui.BadRequestException;
import general.common.ApiEnvelope;
import general.common.MessagesStrings;
import models.common.Study;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import play.libs.Json;
import play.mvc.Http;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static general.common.ApiEnvelope.ErrorCode.VALIDATION_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

@Singleton
public class ApiService {

    private final IOUtils ioUtils;

    @Inject
    private ApiService(IOUtils ioUtils) {
        this.ioUtils = ioUtils;
    }

    public void validateProps(Constraints.Validatable<List<ValidationError>> props) throws BadRequestException {
        List<ValidationError> errors = props.validate();
        if (errors != null && !errors.isEmpty()) {
            String msg = "Error in field '" + errors.get(0).key() + "' - " + errors.get(0).message();
            throw new BadRequestException(msg, VALIDATION_ERROR);
        }
    }

    public <T> T getFieldFromJson(JsonNode json, String fieldName, Class<T> fieldType) throws BadRequestException {
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

    public <T> T getFieldFromJson(JsonNode json, String fieldName, Class<T> fieldType, T defaultValue) throws BadRequestException {
        if (json == null || json.get(fieldName) == null) {
            return defaultValue;
        }
        return getFieldFromJson(json, fieldName, fieldType);
    }

    public boolean getActiveFlagFromJson(JsonNode json) throws BadRequestException {
        return getFieldFromJson(json, "active", Boolean.class);
    }

    /**
     * Controller method needs to be annotated with @BodyParser.Of(BodyParser.Raw.class) for this method to work
     * properly
     */
    public JsonNode getJsonFromBody(Http.Request request) throws BadRequestException, JsonProcessingException {
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
    public String getSessionDataFromBody(Http.Request request) throws JsonProcessingException {
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
     * Normalizes the field with the name 'fieldName' within a JSON object by converting it to a serialized JSON string
     * if the field is an object or an array. If the JSON object has a field 'jsonData' (deprecated name), this is used
     * instead. If the field is missing or already a string, no changes are made.
     */
    public ObjectNode normalizeJsonInputField(JsonNode json, String fieldName)
            throws BadRequestException, JsonProcessingException {
        if (!json.isObject()) {
            throw new BadRequestException("Request body is not a JSON object", ApiEnvelope.ErrorCode.INVALID_JSON);
        }
        ObjectNode jsonObj = (ObjectNode) json;
        if (jsonObj.has("jsonData")) fieldName = "jsonData";

        if (!jsonObj.has(fieldName)) return jsonObj;

        JsonNodeType jsonInputType = jsonObj.get(fieldName).getNodeType();
        if (jsonInputType == JsonNodeType.OBJECT || jsonInputType == JsonNodeType.ARRAY) {
            jsonObj.put(fieldName, Json.mapper().writeValueAsString(jsonObj.get(fieldName)));
        }
        return jsonObj;
    }

    /**
     * Extracts a file from the request body. It can handle different content type headers. It always tries
     * "multipart/form-data". Additionally, it tries all content types in the list "allowedRawTypes".
     */
    public Path extractFile(Http.Request request, String filePartName, List<String> allowedRawTypes)
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
            return ((File) filePart.getFile()).toPath();
        }

        if (allowedRawTypes.stream().anyMatch(contentType::startsWith) || contentType.isEmpty()) {
            Http.RawBuffer raw = request.body().asRaw();
            if (raw == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            // Prefer a temp file if Play stored it on disk (common for larger uploads)
            File rawFile = raw.asFile();
            if (rawFile != null && rawFile.exists() && rawFile.length() > 0) {
                return rawFile.toPath();
            }
            // Fallback: raw bytes (small uploads); write to a temp file
            if (raw.asBytes() == null || raw.asBytes().isEmpty()) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ApiEnvelope.ErrorCode.MISSING_FILE);
            }
            Path tmp = Files.createTempFile("jatos-file-upload-", ".tmp");
            Files.write(tmp, raw.asBytes().toArray());
            return tmp;
        }
        throw new BadRequestException(
                "Unsupported Content-Type '" + contentType + "'. Use multipart/form-data, " + allowedRawTypes);
    }

    public ObjectNode getSessionNode(String sessionData, Long version, boolean asText) throws JsonProcessingException {
        String sessionDataNormalized = sessionData != null ? sessionData : "{}";
        ObjectNode sessionNode = Json.newObject()
                .put("version", version);
        if (asText) {
            sessionNode.put("sessionData", sessionDataNormalized);
        } else {
            JsonNode sessionDataNode = Json.mapper().readTree(sessionDataNormalized);
            sessionNode.set("sessionData", sessionDataNode);
        }
        return sessionNode;
    }

    /**
     * Get a Path to a file in a study assets directory.
     *
     * @param filepath Filepath to the file.
     *
     *                 If it points to a directory (indicated by a trailing '/'), the returned Path consists of filepath
     *                 + filename.
     *
     *                 If it does not point to a directory, it is treated as a path to a file and returned without taken
     *                 the filename parameter into account. This can be used to rename a file.
     *
     *                 This parameter is optional and can be null to signal the path to the file is supposed to be in
     *                 the root of the study assets directory. Another option to signal the root is a single '/'.
     *
     *                 A leading '/' gets removed.
     *
     *                 It can be URL encoded but doesn't have to be.
     * @param filename Filename of the file (without a path).
     * @param study    Study where the study assets belong to
     * @return Path to the file in the study assets
     */
    public Path getAssetsFilePath(String filepath, String filename, Study study) throws BadRequestException {
        String assetsFilePathStr;
        if (!Strings.isNullOrEmpty(filepath)) {
            filepath = Helpers.urlDecode(filepath).trim();
            if (filepath.startsWith("/")) filepath = filepath.substring(1); // remove leading '/'

            if (filepath.endsWith("/")) assetsFilePathStr = filepath + filename;
            else if (filepath.isEmpty()) assetsFilePathStr = filename;
            else assetsFilePathStr = filepath;
        } else {
            assetsFilePathStr = filename;
        }

        try {
            return ioUtils.getFileInStudyAssetsDir(study.getDirName(), assetsFilePathStr);
        } catch (IOException e) {
            throw new BadRequestException("Invalid path: " + assetsFilePathStr, VALIDATION_ERROR);
        }
    }

}
