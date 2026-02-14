package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.gui.BadRequestException;
import models.gui.ApiEnvelope;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import play.libs.Json;

import java.util.List;

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


}
