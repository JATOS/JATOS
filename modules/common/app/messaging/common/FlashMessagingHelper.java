package messaging.common;

import play.api.mvc.Flash;
import play.api.mvc.RequestHeader;
import play.libs.Json;

import static exceptions.common.JatosException.unchecked;

/**
 * Utility class for handling flash messages in web applications and converting them into JSON format. Flash messages
 * are typically used to pass temporary messages (e.g., success, error) between requests, especially after redirects.
 * This class processes the flash messages and organizes them into categorized message lists such as info, success,
 * error, and warning.
 */
public class FlashMessagingHelper {

    public static final String INFO = "info";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String WARNING = "warning";

    public static String asJson(RequestHeader request) {
        Messages messages = new Messages();
        Flash flash = request.flash();
        if (flash.get(INFO).isDefined()) messages.info(flash.get(INFO).get());
        if (flash.get(SUCCESS).isDefined()) messages.info(flash.get(SUCCESS).get());
        if (flash.get(ERROR).isDefined()) messages.info(flash.get(ERROR).get());
        if (flash.get(WARNING).isDefined()) messages.info(flash.get(WARNING).get());
        return unchecked(() -> Json.mapper().writeValueAsString(messages));
    }

}
