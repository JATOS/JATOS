package messaging.common;

import general.common.Http.Context;
import play.libs.typedmap.TypedKey;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses {@link Context}. JATOS has two
 * similar messaging services, this and FlashScopeMessaging. Difference to FlashScopeMessaging:
 * RequestScopeMessaging can have multiple messages for each kind (info/warning/error/success), but
 * it does not survive a redirect (according to Play's documentation, flash scope isn't reliable).
 *
 * @author Kristian Lange
 */
public class RequestScopeMessaging {

    public static final TypedKey<Messages> MESSAGES = TypedKey.create("messages");

    public static String asJson() {
        if (Context.current().args().containsKey(MESSAGES)) {
            return JsonUtils.asJson(Context.current().args().get(MESSAGES));
        } else {
            return "{}";
        }
    }

    private static Messages getMessages() {
        if (!Context.current().args().containsKey(MESSAGES)) {
            Messages messages = new Messages();
            Context.current().args().put(MESSAGES, messages);
        }
        return Context.current().args().get(MESSAGES);
    }

    public static void error(String msg) {
        getMessages().error(msg);
    }

    public static void info(String msg) {
        getMessages().info(msg);
    }

    public static void warning(String msg) {
        getMessages().warning(msg);
    }

    public static void success(String msg) {
        getMessages().success(msg);
    }

}
