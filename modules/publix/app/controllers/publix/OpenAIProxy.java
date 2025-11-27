package controllers.publix;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import general.common.Common;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OpenAIProxy extends Controller {

    private final WSClient ws;

    @Inject
    public OpenAIProxy(WSClient ws) {
        this.ws = ws;
    }

    public CompletionStage<Result> proxy(Http.Request request, String path) {
        if (Strings.isNullOrEmpty(Common.getOpenAIApiKey())) {
            return CompletableFuture.completedFuture(status(401));
        }

        JsonNode body = request.body().asJson();
        return ws.url("https://api.openai.com/v1/" + path)
                .addHeader("Authorization", "Bearer " + Common.getOpenAIApiKey())
                .post(body)
                .thenApply(r -> status(r.getStatus(), r.getBody()));
    }
}