package general.gui;

import models.gui.Messages;
import play.api.mvc.RequestHeader;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses request attrs. JATOS has two
 * similar messaging services, this and FlashScopeMessaging. Difference to FlashScopeMessaging:
 * RequestScopeMessaging can have multiple messages for each kind (info/warning/error/success), but
 * it does not survive a redirect (according to Play's documentation, flash scope isn't reliable).
 *
 * @author Kristian Lange
 */
public class RequestScopeMessaging {

    public static final TypedKey<Messages> MESSAGES = TypedKey.create("messages");

    public static String getAsJson(RequestHeader request) {
        return getAsJson(request.asJava());
    }

    public static String getAsJson(Http.RequestHeader rh) {
        return rh.attrs().containsKey(MESSAGES) ? JsonUtils.asJson(rh.attrs().get(MESSAGES)) : "";
    }

    public static Http.Request init(Http.Request request) {
        if (!request.attrs().containsKey(MESSAGES)) {
            Messages messages = new Messages();
            request = request.addAttr(MESSAGES, messages);
        }
        return request;
    }

    public static Http.Request error(Http.Request request, String msg) {
        request.attrs().get(MESSAGES).error(msg);
        return request;
    }

    public static Http.Request info(Http.Request request, String msg) {
        request.attrs().get(MESSAGES).info(msg);
        return request;
    }

    public static Http.Request warning(Http.Request request, String msg) {
        request.attrs().get(MESSAGES).warning(msg);
        return request;
    }

    public static Http.Request success(Http.Request request, String msg) {
        request.attrs().get(MESSAGES).success(msg);
        return request;
    }

}
