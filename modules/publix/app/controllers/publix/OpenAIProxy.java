package controllers.publix;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class OpenAIProxy extends Controller {

    private final WSClient ws;
    private final String openaiKey;

    @Inject
    public OpenAIProxy(WSClient ws) {
        this.ws = ws;
        this.openaiKey = System.getenv("OPENAI_API_KEY");
    }

    public CompletionStage<Result> proxy(Http.Request request, String path) {
        JsonNode body = request.body().asJson();

        return ws.url("https://api.openai.com/v1/" + path)
                .addHeader("Authorization", "Bearer " + openaiKey)
                .post(body)
                .thenApply(r -> status(r.getStatus(), r.getBody()));
    }
}