package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import json.common.DefaultJson;
import http.common.HttpUtils;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;
import play.mvc.Http;
import play.libs.ws.WSBodyWritables;
import org.apache.pekko.util.ByteString;
import play.Logger;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;


import play.libs.ws.WSResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Serial;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.io.IOException;

import org.apache.pekko.stream.StreamLimitReachedException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Calls only fixed API paths on this installation's configured HTTP listener, never a client-supplied host or URL.
 *
 */
@Singleton
public class McpApiClient implements WSBodyWritables {

    private static final Logger.ALogger LOGGER = Logger.of(McpApiClient.class);

    /**
     * HTTP status and parsed JSON body returned by the local JATOS API.
     */
    public record Reply(int status, JsonNode body) {}

    /**
     * How API data is decoded or delivered. DOWNLOAD_REQUEST returns instructions without fetching the export.
     */
    public enum ResponseFormat {ENVELOPED_JSON, RAW_JSON, RAW_TEXT, DOWNLOAD_LINK, DOWNLOAD_REQUEST, UPLOAD_REQUEST}

    /**
     * Expected API failures with messages safe to show to an MCP client.
     */
    public enum FailureReason {
        TIMEOUT("JATOS API request timed out"),
        TOO_LARGE("JATOS API response exceeds the configured size limit"),
        INVALID_UTF8("API response is not valid UTF-8 text"),
        INVALID_JSON("JATOS API returned invalid JSON"),
        UNAVAILABLE("JATOS API connection failed");

        private final String message;

        /**
         * Associates a failure category with its controlled client-facing message.
         */
        FailureReason(String message) {
            this.message = message;
        }
    }

    /**
     * Separates expected transport and decoding failures from programming errors.
     */
    public static final class ApiFailure extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Retains the cause for diagnosis while exposing only the category's safe message.
         */
        public ApiFailure(FailureReason reason, Throwable cause) {
            super(reason.message, cause);
        }
    }

    private final WSClient ws;
    private final Materializer materializer;
    private final DefaultJson defaultJson;

    /**
     * Creates the loopback API client with its HTTP client, stream materializer and shared JSON mapper.
     */
    @Inject
    public McpApiClient(WSClient ws, Materializer materializer, DefaultJson defaultJson) {
        this.ws = ws;
        this.materializer = materializer;
        this.defaultJson = defaultJson;
    }

    /**
     * Performs an asynchronous GET using the caller's Authorization header and a trusted API-relative path. Redirects
     * are disabled; transport, timeout, size-limit and JSON parsing failures complete the stage exceptionally.
     */
    public CompletionStage<Reply> get(String path, String authorization) {
        return get(path, authorization, ResponseFormat.ENVELOPED_JSON);
    }

    /**
     * Fetches a bounded API response, wrapping raw JSON or UTF-8 text in the usual data envelope.
     */
    public CompletionStage<Reply> get(String path, String authorization, ResponseFormat format) {
        return request("GET", path, authorization, format, null);
    }

    /**
     * Executes one resolved tool request through the API.
     */
    public CompletionStage<Reply> execute(ResolvedCall call, String authorization) {
        return request(call.method(), call.path(), authorization, call.format(), call.body());
    }

    /**
     * Sends one API request without retrying mutations; JSON properties retain their original types.
     */
    private CompletionStage<Reply> request(String method, String path, String authorization, ResponseFormat format, JsonNode body) {
        // Capture synchronous request construction failures in the same completion stage as transport failures.
        return CompletableFuture.completedFuture((Void) null).thenCompose(ignored -> {
            String baseUrl = HttpUtils.getJatosLoopbackBaseUrl() + "jatos/api/v1";
            WSRequest request = ws.url(baseUrl + path)
                    .setFollowRedirects(false)
                    .setRequestTimeout(Common.getJatosMcpApiTimeout())
                    .addHeader("Authorization", authorization)
                    .addHeader("Accept", format == ResponseFormat.RAW_TEXT ? "*/*" : "application/json");
            if (body != null && body.isTextual()) {
                var bytes = ByteString.fromString(body.asText(), StandardCharsets.UTF_8);
                Http.MultipartFormData.Part<Source<ByteString, ?>> file =
                        new Http.MultipartFormData.FilePart<>("studyAssetsFile", "asset.txt", "text/plain; charset=utf-8",
                                Source.single(bytes));
                request.setBody(multipartBody(Source.single(file)));
            } else if (body != null) {
                request.setContentType("application/json").setBody(defaultJson.objAsJson(body));
            }
            request.setMethod(method);
            return request.stream().thenCompose(response -> readReply(response, path, format));
        }).handle((reply, failure) -> {
            if (failure == null) return reply;
            Throwable cause = classifyFailure(failure);
            if (cause instanceof ApiFailure) logFailure(path, cause);
            throw new CompletionException(cause);
        });
    }

    /**
     * Reads a bounded response body while preserving an HTTP error status if its body cannot be read.
     */
    private CompletionStage<Reply> readReply(WSResponse response, String path, ResponseFormat format) {
        int status = response.getStatus();
        return response.getBodyAsSource()
                .limitWeighted(Common.getJatosMcpMaxResponseSize(), bytes -> (long) bytes.size())
                .completionTimeout(Common.getJatosMcpApiTimeout())
                .runFold(ByteString.emptyByteString(), ByteString::concat, materializer)
                .thenApply(bytes -> new Reply(status, decode(bytes, status, format)))
                .exceptionally(failure -> {
                    Throwable cause = classifyFailure(failure);
                    if (!isSuccess(status) && cause instanceof ApiFailure) {
                        logFailure(path, cause);
                        return new Reply(status, errorBody());
                    }
                    throw new CompletionException(cause);
                });
    }

    /**
     * Accepts all successful API statuses, including 201 for study creation and import.
     */
    public static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /**
     * Decodes successful payloads by format and keeps API error envelopes unchanged.
     */
    JsonNode decode(ByteString bytes, int status, ResponseFormat format) {
        if (!isSuccess(status)) {
            try {
                JsonNode body = parseJson(bytes);
                return body.isObject() ? body : errorBody();
            } catch (ApiFailure e) {
                return errorBody();
            }
        }
        if (format == ResponseFormat.ENVELOPED_JSON || format == ResponseFormat.DOWNLOAD_LINK) return parseJson(bytes);
        var envelope = JsonNodeFactory.instance.objectNode();
        if (format == ResponseFormat.RAW_JSON) {
            envelope.set("data", parseJson(bytes));
        } else {
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toArray())).toString();
            } catch (CharacterCodingException e) {
                throw new ApiFailure(FailureReason.INVALID_UTF8, e);
            }
            envelope.put("data", text);
        }
        return envelope;
    }

    /**
     * Parses JSON, classifying only malformed response content as an expected API failure.
     */
    private JsonNode parseJson(ByteString bytes) {
        try {
            JsonNode value = defaultJson.mapper().readTree(bytes.toArray());
            if (value == null || value.isMissingNode()) throw new ApiFailure(FailureReason.INVALID_JSON, null);
            return value;
        } catch (IOException e) {
            // Reading an in-memory byte array can only fail here while decoding JSON.
            throw new ApiFailure(FailureReason.INVALID_JSON, e);
        }
    }

    /**
     * Supplies a safe message when an HTTP error has no usable JSON error body.
     */
    private static JsonNode errorBody() {
        return JsonNodeFactory.instance.objectNode().set("error", JsonNodeFactory.instance.objectNode().put("message", "Request failed"));
    }

    /**
     * Removes asynchronous wrappers without discarding a classified failure's safe message.
     */
    public static Throwable unwrapFailure(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /**
     * Recognizes expected stream/network failures and leaves programming errors unchanged.
     */
    static Throwable classifyFailure(Throwable failure) {
        Throwable cause = unwrapFailure(failure);
        if (cause instanceof ApiFailure) return cause;
        if (cause instanceof StreamLimitReachedException) return new ApiFailure(FailureReason.TOO_LARGE, cause);
        if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.http.HttpTimeoutException)
            return new ApiFailure(FailureReason.TIMEOUT, cause);
        if (cause instanceof IOException) return new ApiFailure(FailureReason.UNAVAILABLE, cause);
        return cause;
    }

    /**
     * Logs the underlying failure type once without exception messages that might expose response content.
     */
    private static void logFailure(String path, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        LOGGER.warn("MCP loopback API request {} failed: {}", path, cause.getClass().getName());
    }
}
