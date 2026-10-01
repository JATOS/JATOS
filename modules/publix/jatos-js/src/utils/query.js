/** Encodes the flat scalar query parameters used by study navigation. */
export function encodeQuery(parameters) {
    return Object.entries(parameters).map(([key, value]) =>
        encodeURIComponent(key) + "=" + encodeURIComponent(value == null ? "" : value)
    ).join("&");
}
