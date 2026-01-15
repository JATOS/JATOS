package controllers.publix;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import daos.common.StudyResultDao;
import general.common.Common;
import play.db.jpa.Transactional;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

@SuppressWarnings("deprecation")
@PublixAccessLogging
public class OpenAiProxy extends Controller {

    private final WSClient ws;
    private final StudyResultDao studyResultDao;

    @Inject
    public OpenAiProxy(WSClient ws, StudyResultDao studyResultDao) {
        this.ws = ws;
        this.studyResultDao = studyResultDao;
    }

    @Transactional
    public CompletionStage<Result> proxy(Http.Request request, String path, String studyResultUuid) {
        if (!Common.isOpenAiAllowed()) {
            return completedFuture(forbidden(
                    Json.newObject()
                            .put("apiVersion", Common.getJatosApiVersion())
                            .put("error", "OpenAI API is not allowed")
            ));
        }
        if (!studyResultDao.isStudyRunning(studyResultUuid)) {
            return completedFuture(forbidden(
                    Json.newObject()
                            .put("apiVersion", Common.getJatosApiVersion())
                            .put("error", "Study not running")
            ));
        }
        if (Common.getOpenAiCallLimit() >= 0 && !studyResultDao.checkAndIncrementOpenAiApiCount(studyResultUuid, Common.getOpenAiCallLimit())) {
            return completedFuture(tooManyRequests(
                    Json.newObject()
                            .put("apiVersion", Common.getJatosApiVersion())
                            .put("error", "OpenAI API call limit reached")
            ));
        }

        // Ensure we have a path without leading /
        String formattedPath = path.startsWith("/") ? path.substring(1) : path;

        WSRequest wsRequest = ws.url("https://api.openai.com" + Common.getOpenAiUrlBasePath() + formattedPath)
                .addHeader("Authorization", "Bearer " + Common.getOpenAiApiKey())
                .setRequestTimeout(Duration.ofSeconds(Common.getOpenAiTimeout()));

        // Pass through Content-Type if present, default to JSON
        String contentType = request.contentType().orElse("application/json");
        wsRequest.addHeader("Content-Type", contentType);

        // Handle body for POST and PUT
        JsonNode body = request.body().asJson();
        if (body != null && !body.isNull()) {
            wsRequest.setBody(body);
        }

        return wsRequest.execute(request.method())
                // Return OpenAI's response as binary data
                .thenApply(r -> status(r.getStatus(), r.asByteArray()).as(r.getContentType()))
                .exceptionally(e -> internalServerError(
                        Json.newObject()
                                .put("apiVersion", Common.getJatosApiVersion())
                                .put("error", "Proxy error: " + e.getMessage())
                ));
    }
}