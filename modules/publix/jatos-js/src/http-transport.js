import {createDeferred} from "./jatos-promise.js";

/**
 * Direct HTTP requests used outside the result-data worker queue.
 * `retry.times` counts total attempts. Success/error callbacks run per attempt;
 * the returned promise settles once.
 */
export function requestHttp(options) {
    const deferred = createDeferred();
    let attemptsLeft = options.retry?.times ?? 1;

    function attempt() {
        const xhr = new XMLHttpRequest();
        xhr.open(options.method || "GET", options.url, true);
        xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
        xhr.setRequestHeader("Accept", options.dataType === "json"
            ? "application/json, text/javascript, */*; q=0.01" : "*/*");
        xhr.timeout = options.timeout || 0;

        function fail(textStatus, error = textStatus) {
            const response = {
                status: xhr.status,
                statusText: textStatus === "timeout" ? "timeout" : (xhr.statusText || textStatus),
                responseText: xhr.responseText
            };
            options.error?.(response, textStatus, error);
            options.statusCode?.[xhr.status]?.(response, textStatus, error);
            if (attemptsLeft > 1) {
                attemptsLeft--;
                let delay = options.retry?.timeout;
                const retryAfter = xhr.getResponseHeader("Retry-After");
                if (retryAfter) {
                    const parsed = isNaN(retryAfter)
                        ? Date.parse(retryAfter) - Date.now() : parseInt(retryAfter, 10) * 1000;
                    if (!isNaN(parsed) && parsed >= 0) delay = parsed;
                }
                if (delay) setTimeout(attempt, delay);
                else attempt();
            } else {
                deferred.reject(response, textStatus, error);
            }
        }

        xhr.onload = () => {
            if (!(xhr.status >= 200 && xhr.status < 300) && xhr.status !== 304) {
                fail("error", xhr.statusText);
                return;
            }
            let data;
            const textStatus = xhr.status === 204 ? "nocontent" : xhr.status === 304 ? "notmodified" : "success";
            if (xhr.status !== 204 && xhr.status !== 304) {
                data = xhr.responseText;
                const json = options.dataType === "json" || (!options.dataType &&
                    /\bjson\b/i.test(xhr.getResponseHeader("Content-Type") || ""));
                if (json) {
                    try { data = JSON.parse(data); }
                    catch (error) { fail("parsererror", error); return; }
                }
            }
            options.success?.(data, textStatus, xhr);
            options.statusCode?.[xhr.status]?.(data, textStatus, xhr);
            deferred.resolve(data, textStatus, xhr);
        };
        xhr.onerror = () => fail("error");
        xhr.ontimeout = () => fail("timeout");
        xhr.onabort = () => fail("abort");
        xhr.send(null);
    }

    attempt();
    return deferred.promise();
}

/** Formats transport failures while preserving the existing public error messages. */
export function getHttpErrorMessage(response) {
    if (response.statusText === "timeout") {
        return "JATOS server not responding";
    }
    return response.statusText + ": " +
        (response.responseText || "Error during Ajax call to JATOS server.");
}
