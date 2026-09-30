import {createDeferred, isDeferredPending} from "./jatos-promise.js";
import {call, callWithArgs} from "./utils/callbacks.js";

export function createHttpLoop({isInitialized}) {
    let worker;
    let counter = 0;
    let idleDeferred;
    const waitingRequests = {};

    function start() {
        worker = new Worker("jatos-publix/javascripts/http-loop-worker.js");
        worker.addEventListener("message", event => handleMessage(event.data), false);
    }

    function send(request, onSuccess, onError) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return createDeferred().reject();
        }

        const deferred = createDeferred();
        deferred.done(function () {
            call(onSuccess);
        });
        deferred.fail(function (err) {
            callWithArgs(onError, err);
        });

        request.id = counter++;
        waitingRequests[request.id] = deferred;
        if (!isDeferredPending(idleDeferred)) {
            idleDeferred = createDeferred();
        }
        worker.postMessage(request);

        return deferred;
    }

    function handleMessage(msg) {
        const deferred = waitingRequests[msg.requestId];
        delete waitingRequests[msg.requestId];
        if (msg.status === 200) {
            deferred.resolve();
        } else {
            const errMsg = [msg.status, msg.statusText, msg.error]
                .filter(function (s) { return s; }).join(", ");
            deferred.reject(msg.method + " to " + msg.url + " failed: " + errMsg,
                msg.status, msg.statusText, msg.error);
        }

        if (Object.keys(waitingRequests).length === 0 && isDeferredPending(idleDeferred)) {
            idleDeferred.resolve();
        }
    }

    function isBusy() {
        return isDeferredPending(idleDeferred);
    }

    function whenIdle(callback) {
        if (isBusy()) {
            idleDeferred.always(callback);
        } else {
            callback();
        }
    }

    function terminate() {
        worker.terminate();
    }

    function getCounter() {
        return counter;
    }

    return {getCounter, isBusy, send, start, terminate, whenIdle};
}
