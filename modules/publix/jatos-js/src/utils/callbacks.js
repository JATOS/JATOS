/**
 * Checks if a function exists and calls it.
 *
 * @param {function} f Function to be called
 */
export const call = (f) => {
    if (f && typeof f == 'function') f();
};

/**
 * Calls one function with none, one or multiple arguments. Checks if the function exists.
 *
 * @param {function} f Function to be called
 * @param  {...any} args Arguments to be used with the function
 */
export const callWithArgs = (f, ...args) => {
    if (f && typeof f == 'function') args.length ? f(...args) : f();
};

/**
 * Calls multiple functions with the given argument. Often used for logging to multiple destinations.
 * Checks if the functions exist.
 *
 * @param {*} arg
 * @param  {...function} functions
 */
export const callMany = (arg, ...functions) => functions.forEach((f) => {
    if (f && typeof f === 'function') f(arg);
});
