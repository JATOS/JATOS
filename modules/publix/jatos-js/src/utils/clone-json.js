export function cloneJsonObj(obj) {
    // Primitive values, null, and undefined can be returned unchanged.
    if (null === obj || "object" != typeof obj) return obj;

    // Clone arrays recursively.
    if (obj instanceof Array) {
        const copy = [];
        const len = obj.length;
        for (let i = 0; i < len; i++) {
            copy[i] = cloneJsonObj(obj[i]);
        }
        return copy;
    }

    // Clone object properties recursively.
    if (obj instanceof Object) {
        const copy = {};
        for (const attr in obj) {
            if (obj.hasOwnProperty(attr)) copy[attr] = cloneJsonObj(obj[attr]);
        }
        return copy;
    }

    throw new Error("Unable to copy obj! Its type isn't supported.");
}
