/**
 * Creates the subset of jQuery Deferred used and returned by jatos.js.
 *
 * The promise is a standards-compatible thenable, but done, fail, and always
 * deliberately retain jQuery's synchronous late-handler behavior.
 */
function createLegacyDeferred() {
    let currentState = "pending";
    let settledArgs = [];
    let settledContext;
    const doneCallbacks = [];
    const failCallbacks = [];
    const progressCallbacks = [];

    const promiseMethods = {
        state: function () {
            return currentState;
        },

        always: function (...callbacks) {
            this.done(...callbacks);
            this.fail(...callbacks);
            return this;
        },

        catch: function (onRejected) {
            return this.then(null, onRejected);
        },

        done: function (...callbacks) {
            addCallbacks(doneCallbacks, callbacks);
            if (currentState === "resolved") fireCallbacks(callbacks, settledContext, settledArgs);
            return this;
        },

        fail: function (...callbacks) {
            addCallbacks(failCallbacks, callbacks);
            if (currentState === "rejected") fireCallbacks(callbacks, settledContext, settledArgs);
            return this;
        },

        progress: function (...callbacks) {
            if (currentState === "pending") addCallbacks(progressCallbacks, callbacks);
            return this;
        },

        then: function (onFulfilled, onRejected) {
            const chained = createLegacyDeferred();
            const chainedPromise = chained.promise();

            this.done(function (...args) {
                settleChained(chained, chainedPromise, onFulfilled, "resolve", this, args);
            });
            this.fail(function (...args) {
                settleChained(chained, chainedPromise, onRejected, "reject", this, args);
            });

            return chainedPromise;
        },

        // jQuery retains pipe as an older name for promise transformation.
        pipe: function (onFulfilled, onRejected) {
            return this.then(onFulfilled, onRejected);
        },

        promise: function (target) {
            if (target != null) return Object.assign(target, promiseMethods);
            return promise;
        }
    };

    const promise = Object.assign({}, promiseMethods);
    const deferred = Object.assign({}, promiseMethods, {
        notify: function (...args) {
            return this.notifyWith(this === deferred ? undefined : this, args);
        },

        notifyWith: function (context, args) {
            if (currentState === "pending") fireCallbacks(progressCallbacks, context, toArray(args));
            return this;
        },

        reject: function (...args) {
            return this.rejectWith(this === deferred ? undefined : this, args);
        },

        rejectWith: function (context, args) {
            if (currentState !== "pending") return this;
            currentState = "rejected";
            settledContext = context;
            settledArgs = toArray(args);
            fireCallbacks(failCallbacks, settledContext, settledArgs);
            return this;
        },

        resolve: function (...args) {
            return this.resolveWith(this === deferred ? undefined : this, args);
        },

        resolveWith: function (context, args) {
            if (currentState !== "pending") return this;
            currentState = "resolved";
            settledContext = context;
            settledArgs = toArray(args);
            fireCallbacks(doneCallbacks, settledContext, settledArgs);
            return this;
        }
    });

    return deferred;
}

function addCallbacks(target, callbacks) {
    callbacks.flat(Infinity).forEach(callback => {
        if (typeof callback === "function") target.push(callback);
    });
}

function fireCallbacks(callbacks, context, args) {
    callbacks.flat(Infinity).forEach(callback => {
        if (typeof callback === "function") callback.apply(context, args);
    });
}

function settleChained(chained, chainedPromise, handler, fallback, context, args) {
    setTimeout(function () {
        if (typeof handler !== "function") {
            chained[fallback + "With"](context, args);
            return;
        }

        try {
            const result = handler.apply(context, args);
            adoptResult(chained, chainedPromise, result);
        } catch (error) {
            chained.reject(error);
        }
    });
}

function adoptResult(chained, chainedPromise, result) {
    if (result === chainedPromise) {
        chained.reject(new TypeError("Thenable self-resolution"));
    } else if (result && typeof result.then === "function") {
        result.then(
            (...args) => chained.resolve(...args),
            (...args) => chained.reject(...args)
        );
    } else {
        chained.resolve(result);
    }
}

function toArray(args) {
    return args == null ? [] : Array.from(args);
}

export function createLegacyPromiseCompatibility() {
    return {
        createDeferred: createLegacyDeferred,

        rejectedPromise: function (errorMsg) {
            const deferred = createLegacyDeferred();
            deferred.reject(errorMsg);
            return deferred.promise();
        }
    };
}

export function isDeferredPending(deferred) {
    return typeof deferred != 'undefined' && deferred.state() === 'pending';
}
