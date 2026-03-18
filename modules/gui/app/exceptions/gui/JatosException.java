package exceptions.gui;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.ApiEnvelope;
import general.common.ApiEnvelope.ErrorCode;
import play.api.libs.json.JsValue;

import static general.common.ApiEnvelope.ErrorCode.UNSPECIFIED;

public class JatosException extends Exception {

    private ErrorCode errorCode = UNSPECIFIED;

    public JatosException(String message) {
        super(message);
    }

    public JatosException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public JsonNode asApiJsonNode() {
        return ApiEnvelope.wrap(this.getMessage(), errorCode).asJsonNode();
    }

    public JsValue asApiJsValue() {
        return ApiEnvelope.wrap(this.getMessage(), errorCode).asJsValue();
    }

}
