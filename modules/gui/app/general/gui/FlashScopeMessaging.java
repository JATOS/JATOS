package general.gui;

import models.gui.Messages;
import play.api.mvc.RequestHeader;
import scala.Option;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses Play's flash scope. JATOS has
 * two similar messaging services, this and one and RequestScopeMessaging. Difference to
 * RequestScopeMessaging: FlashScopeMessaging has only one of each kind (info/warning/error/
 * success), but it survives a redirect (according to Play's documentation, flash scope isn't
 * reliable).
 *
 * @author Kristian Lange
 */
public class FlashScopeMessaging {

    public static final String INFO = "info";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String WARNING = "warning";

    public static String getAsJson(RequestHeader request) {
        Messages messages = null;
        Option<String> info = request.flash().get(INFO);
        Option<String> success = request.flash().get(SUCCESS);
        Option<String> error = request.flash().get(ERROR);
        Option<String> warning = request.flash().get(WARNING);
        if (info.isDefined() || success.isDefined() || error.isDefined() || warning.isDefined()) {
            messages = new Messages();
            messages.info(info.getOrElse(null));
            messages.success(success.getOrElse(null));
            messages.error(error.getOrElse(null));
            messages.warning(warning.getOrElse(null));
        }
        return JsonUtils.asJson(messages);
    }

}
