import {Deferred} from "./vendor/jquery-deferred.js";

/**
 * An awaitable, jQuery-compatible promise facade returned by asynchronous
 * jatos.js functions. It is a thenable, but it is not a native `Promise`.
 *
 * `done`, `fail`, and `always` preserve the jQuery-compatible synchronous behavior when
 * registered after settlement. The facade does not expose `resolve` or `reject`.
 *
 * @typedef {Object} JatosPromise
 * @property {function(...Function): JatosPromise} done Adds success handlers.
 * @property {function(...Function): JatosPromise} fail Adds failure handlers.
 * @property {function(...Function): JatosPromise} always Adds settlement handlers.
 * @property {function(Function=, Function=, Function=): JatosPromise} then Returns a chained JatosPromise.
 * @property {function(Function=): JatosPromise} catch Returns a chained JatosPromise.
 * @property {function(Function=, Function=, Function=): JatosPromise} pipe Applies synchronous jQuery-compatible filters.
 * @property {function(...Function): JatosPromise} progress Adds progress handlers.
 * @property {function(): JatosPromise} promise Returns this read-only facade.
 * @property {function(): ("pending"|"resolved"|"rejected")} state Returns its settlement state.
 */

/**
 * The internal, writable side of a JatosPromise.
 * @typedef {JatosPromise & {
 *   resolve: (...args: any[]) => JatosDeferred,
 *   reject: (...args: any[]) => JatosDeferred,
 *   notify: (...args: any[]) => JatosDeferred,
 *   resolveWith: (context: any, args?: any[]) => JatosDeferred,
 *   rejectWith: (context: any, args?: any[]) => JatosDeferred,
 *   notifyWith: (context: any, args?: any[]) => JatosDeferred
 * }} JatosDeferred
 */

// Keep the vendored dependency behind JATOS helpers. jQuery Deferred supplies
// all promise, callback, progress, and scheduling behavior.
/** @returns {JatosDeferred} */
export function createDeferred() {
    // jQuery assembles these methods dynamically; declare its interface here.
    return /** @type {JatosDeferred} */ (Deferred());
}

/** @returns {JatosPromise} */
export function rejectedPromise(error) {
    return createDeferred().reject(error).promise();
}

export function isDeferredPending(deferred) {
    return deferred !== undefined && deferred.state() === 'pending';
}
