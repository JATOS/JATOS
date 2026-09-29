/**
 * Keeps jatos.js's jQuery-style promise contract behind one internal boundary.
 * The getter is used because jatos.jQuery is loaded asynchronously during startup.
 */
export function createLegacyPromiseCompatibility(getJQuery) {
    return {
        createDeferred: function () {
            return getJQuery().Deferred();
        },

        rejectedPromise: function (errorMsg) {
            var deferred = getJQuery().Deferred();
            deferred.reject(errorMsg);
            return deferred.promise();
        }
    };
}

/**
 * Checks if the given jQuery Deferred or Promise object exists and is pending.
 */
export function isDeferredPending(deferred) {
    return typeof deferred != 'undefined' && deferred.state() === 'pending';
}
