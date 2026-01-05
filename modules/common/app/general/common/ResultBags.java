package general.common;

import play.mvc.Call;

import java.io.File;

public class ResultBags {

    /**
     * @param file The file to send.
     * @return The ResultBag.
     */
    public static ResultBag withFile(File file) {
        return new ResultBag(file);
    }

    public static ResultBag empty() {
        return new ResultBag(null);
    }

    public static ResultBag redirect(Call call) {
        return (ResultBag) play.mvc.Results.redirect(call);
    }

    public static ResultBag notFound(String content) {
        return (ResultBag) play.mvc.Results.notFound(content);
    }
}
