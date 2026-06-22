package messaging.common;

import http.common.Http.Context;
import play.libs.Json;
import play.libs.typedmap.TypedKey;

import static exceptions.common.JatosException.unchecked;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses {@link Context}. RequestScopeMessaging can have
 * multiple messages for each kind (info/warning/error/success), but it does not survive a redirect (unlike Play's flash
 * scope).
 */
public class RequestScopeMessaging {

    public static final TypedKey<Messages> MESSAGES = TypedKey.create("messages");

    public static String asJson() {
        if (Context.current().args().containsKey(MESSAGES)) {
            return unchecked(() -> Json.mapper().writeValueAsString(Context.current().args().get(MESSAGES)));
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
