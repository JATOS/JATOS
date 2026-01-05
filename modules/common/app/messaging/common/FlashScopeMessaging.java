package messaging.common;

import play.api.mvc.Flash;
import play.api.mvc.RequestHeader;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses Play's flash scope. JATOS has two similar messaging
 * services, this and one and RequestScopeMessaging. Difference to RequestScopeMessaging: FlashScopeMessaging has only
 * one of each kind (info/warning/error/ success), but it survives a redirect (according to Play's documentation, flash
 * scope isn't reliable).
 *
 * @author Kristian Lange
 */
public class FlashScopeMessaging {

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
        return JsonUtils.asJson(messages);
    }

}
