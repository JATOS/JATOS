import {callMany, callWithArgs} from "./utils/callbacks.js";
import {createDeferred, rejectedPromise, isDeferredPending} from "./jatos-promise.js";

/**
 * Manages outgoing batch or group session patches: validates sends, assigns action IDs,
 * tracks acknowledgements and failures, and settles promises and callbacks on completion
 * or timeout. Each sender owns its pending writes and action counter.
 */
export function createSessionSender({kind, getChannel, isVersioning, getMaxRetries, sync,
    createMessage, patchesKey, studyRunState, getTimeout}) {
    let counter = 0;
    let pending;
    const timeouts = {};

    function getValidationError(patches) {
        const channel = getChannel();
        if (!channel || channel.readyState !== channel.OPEN) {
            return {message: `Can't send ${kind} session patch. No open ${kind} channel. Patch: ${patches.op} ${patches.path}.`, log: console.error};
        }
        if (isVersioning() && isDeferredPending(pending)) {
            return {message: `Can send only one ${kind} session patch at a time. Patch: ${patches.op} ${patches.path}.`, log: console.error};
        }
        if (studyRunState.invalid) {
            return {message: `Can't send ${kind} session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`, log: console.warn};
        }
    }

    function isRetryChannelAvailable(channel) {
        return !studyRunState.ending && !studyRunState.invalid && isVersioning()
            && getChannel() === channel && channel.readyState === channel.OPEN;
    }

    function canRetry(operation, channel, failure) {
        return operation.versioning && isRetryChannelAvailable(channel)
            && failure?.errorCode === "SESSION_VERSION_CONFLICT"
            && Number.isSafeInteger(failure.version)
            && operation.retries < operation.maxRetries;
    }

    function retryAfterSync(operation, channel, failure) {
        if (!canRetry(operation, channel, failure)) return false;
        operation.retries++;
        sync.waitFor(failure.version, error => {
            if (!error && !isRetryChannelAvailable(channel)) {
                error = "Session channel unavailable for retry";
            }
            if (error) {
                callWithArgs(operation.onError, error);
                operation.deferred.reject(error);
            } else sendAttempt(operation);
        });
        return true;
    }

    function sendAttempt(operation) {
        const channel = getChannel();
        const id = counter++;
        const message = createMessage(id, operation.patches, operation.versioning);
        try {
            const serializedMessage = JSON.stringify(message);
            // Preserve the first attempt's patch even if the caller later mutates it.
            if (operation.maxRetries > 0 && operation.retries === 0) {
                operation.patches = JSON.parse(serializedMessage)[patchesKey];
            }
            channel.send(serializedMessage);
            setChannelSendingTimeoutAndPromiseResolution(
                operation.deferred, timeouts, id, operation.onSuccess, operation.onError,
                failure => retryAfterSync(operation, channel, failure), getTimeout());
        } catch (error) {
            callMany(error, operation.onError, console.error);
            operation.deferred.reject();
        }
    }

    function sendSessionPatch(patches, onSuccess, onError) {
        const error = getValidationError(patches);
        if (error) {
            callMany(error.message, onError, error.log);
            return rejectedPromise(error.message);
        }

        const deferred = createDeferred();
        if (isVersioning()) pending = deferred;
        const configuredRetries = getMaxRetries();
        // One operation owns the promise, original patch, and retry budget across attempts.
        const operation = {
            deferred, onSuccess, onError,
            maxRetries: Number.isSafeInteger(configuredRetries) && configuredRetries > 0 ? configuredRetries : 0,
            versioning: !!isVersioning(),
            patches: patches.constructor === Array ? patches : [patches],
            retries: 0
        };
        sendAttempt(operation);
        return deferred.promise();
    }

    return {
        send: sendSessionPatch,
        hasPending: id => timeouts.hasOwnProperty(id),
        acknowledge(id, message) {
            if (timeouts.hasOwnProperty(id)) timeouts[id].cancel(message);
        },
        reject(id, message, failure) {
            if (timeouts.hasOwnProperty(id)) timeouts[id].trigger(message, failure);
        }
    };
}

/**
 * Sets a timeout and puts an object with two functions, 'cancel' and 'trigger'
 * into the given sessionTimeouts
 */
function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts,
                                                      sessionActionId, onSuccess, onError, retry, timeout) {
    const timeoutId = setTimeout(function () {
        delete sessionTimeouts[sessionActionId];
        callWithArgs(onError, "Timeout sending session patch");
        deferred.reject("Timeout sending session patch");
    }, timeout);

    // Create a new timeout object with two functions: 1) to cancel
    // the timeout and 2) to trigger the timeout prematurely
    sessionTimeouts[sessionActionId] = {
        cancel: function (msg) {
            clearTimeout(timeoutId);
            delete sessionTimeouts[sessionActionId];
            callWithArgs(onSuccess, msg);
            deferred.resolve(msg);
        },
        trigger: function (msg, failure) {
            clearTimeout(timeoutId);
            delete sessionTimeouts[sessionActionId];
            if (retry(failure)) return;
            callWithArgs(onError, msg);
            deferred.reject(msg);
        }
    };
}
