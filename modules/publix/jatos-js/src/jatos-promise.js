import {Deferred} from "./vendor/jquery-deferred.js";

// Keep the vendored dependency behind JATOS helpers. jQuery Deferred supplies
// all promise, callback, progress, and scheduling behavior.
export function createDeferred() {
    return Deferred();
}

export function rejectedPromise(error) {
    return createDeferred().reject(error).promise();
}

export function isDeferredPending(deferred) {
    return deferred !== undefined && deferred.state() === 'pending';
}
