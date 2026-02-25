package models.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import play.api.libs.json.JsValue;
import play.libs.Json;
import utils.common.JsonUtils;

import static play.api.libs.json.Json.parse;

/**
 * Envelope/wrapper for API responses.
 *
 * This class standardizes the JSON shape returned by the API by always including the {@code apiVersion} and optionally
 * including either a successful payload ({@code message}/{@code data}) or an error description ({@code error}). It is
 * typically created via one of the static {@code wrap(...)} factory methods.
 *
 * Use {@link #asJsonNode()} to obtain a {@code JsonNode} representation of the envelope, or {@link #asJsValue()} to
 * obtain a Play {@code JsValue}.
 *
 * Note: An instance is intended to represent either a success response or an error response. If an {@link ErrorCode} is
 * present, the envelope is treated as an error regardless of any provided data.
 */
public class ApiEnvelope {

    public enum ErrorCode {
        VALIDATION_ERROR,
        INVALID_REQUEST,
        INVALID_JSON,
        INVALID_AUTH_METHOD,
        NOT_FOUND,
        AUTH_ERROR,
        NO_ACCESS,
        LDAP_ERROR,
        FORBIDDEN,
        CONFIG_ERROR,
        NOT_ACCEPTABLE,
        MISSING_FILE,
        FILE_ERROR,
        STUDY_LOCKED,
        TOO_LARGE,
        CLIENT_ERROR,
        UNSPECIFIED,
        UNEXPECTED_ERROR,
    }

    private final String apiVersion = Common.getJatosApiVersion();
    private String message;
    private JsonNode data;
    private ErrorCode errorCode;

    public ApiEnvelope() {
    }

    public static ApiEnvelope wrap(Object obj) {
        ApiEnvelope env = new ApiEnvelope();
        env.data = JsonUtils.asJsonNode(obj);
        return env;
    }

    public static ApiEnvelope wrap(JsonNode data) {
        ApiEnvelope env = new ApiEnvelope();
        env.data = data;
        return env;
    }

    public static ApiEnvelope wrap(String message) {
        ApiEnvelope env = new ApiEnvelope();
        env.message = message;
        return env;
    }

    public static ApiEnvelope wrap(String message, JsonNode data) {
        ApiEnvelope env = new ApiEnvelope();
        env.message = message;
        env.data = data;
        return env;
    }

    public static ApiEnvelope wrap(String message, ApiEnvelope.ErrorCode errorCode) {
        ApiEnvelope env = new ApiEnvelope();
        env.message = message;
        env.errorCode = errorCode;
        return env;
    }

    public JsonNode asJsonNode() {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("apiVersion", apiVersion);
        if (errorCode == null) {
            if (message != null) node.put("message", message);
            if (data != null) node.set("data", data);
        } else {
            ObjectNode error = Json.mapper().createObjectNode();
            error.put("code", errorCode.name());
            if (message != null) error.put("message", message);
            node.set("error", error);
        }
        return node;
    }

    public JsValue asJsValue() {
        return parse(asJsonNode().toString());
    }
}
