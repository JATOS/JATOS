package controllers.publix;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import daos.common.StudyResultDao;
import general.common.Common;
import play.db.jpa.Transactional;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

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
            return completedFuture(forbidden("OpenAI API is not allowed"));
        }
        if (!studyResultDao.isStudyRunning(studyResultUuid)) {
            return completedFuture(forbidden("Study not running"));
        }
        if (Common.getOpenAiCallLimit() >= 0 && !studyResultDao.checkAndIncrementOpenAiApiCount(studyResultUuid, Common.getOpenAiCallLimit())) {
            return completedFuture(tooManyRequests("OpenAI API call limit reached"));
        }

        JsonNode body = request.body().asJson();
        return ws.url("https://api.openai.com" + Common.getOpenAiUrlBasePath() + path)
                .addHeader("Authorization", "Bearer " + Common.getOpenAiApiKey())
                .setBody(body)
                .execute(request.method())
                .thenApply(r -> status(r.getStatus(), r.getBody()));
    }
}