/**
 * Converts row objects to delimited text with CRLF between records.
 * Objects and arrays are written as JSON text in one field.
 * Null, undefined, and missing values become quoted empty fields.
 *
 * @param {Object[]} dataArray - One object per row; keys may differ.
 * @param {string} [csvSeparator=','] - Field separator.
 * @param {boolean} [useBOM=false] - Whether to prepend a Unicode BOM.
 * @returns {string} Delimited text, or an empty string if there are no rows.
 * @throws {TypeError} If an argument or cell value is unsupported.
 */
export function convertToCSV(dataArray, csvSeparator = ",", useBOM = false) {
    if (!Array.isArray(dataArray)) {
        throw new TypeError("dataArray must be an array of row objects");
    }
    if (typeof csvSeparator !== "string" || !csvSeparator || /["\r\n]/.test(csvSeparator)) {
        throw new TypeError("csvSeparator must be a non-empty string without double quotes, CR, or LF");
    }
    if (typeof useBOM !== "boolean") {
        throw new TypeError("useBOM must be a boolean");
    }
    if (!dataArray.length) return "";

    const keys = new Set();
    for (const [i, row] of dataArray.entries()) {
        if (row == null || typeof row !== "object" || Array.isArray(row)) {
            throw new TypeError("Row at index " + i + " must be a non-null, non-array object");
        }
        for (const key of Object.keys(row)) keys.add(key);
    }
    const headers = [...keys];

    // Prevent characters inside a value from being mistaken for CSV syntax.
    const escapeCsvField = field => {
        if (!field || csvSeparator.length > 1 ||
            field.includes(csvSeparator) || /["\r\n\u2028\u2029]/.test(field)) {
            return '"' + field.replace(/"/g, '""') + '"';
        }
        return field;
    };

    const records = [headers.map(escapeCsvField).join(csvSeparator)];
    for (const [i, row] of dataArray.entries()) {
        const fields = headers.map(header => {
            const value = Object.prototype.hasOwnProperty.call(row, header) ? row[header] : undefined;

            if (value == null) return '""';

            if (["string", "number", "boolean"].includes(typeof value)) {
                return escapeCsvField(String(value));
            }

            const location = `row index ${i}, column ${JSON.stringify(header)}`;

            if (typeof value === "object") {
                try {
                    const json = JSON.stringify(value);
                    if (json !== undefined) return escapeCsvField(json);
                } catch (error) {
                    const serializationError = new TypeError(`Cannot serialize value at ${location} as JSON`);
                    serializationError.cause = error;
                    throw serializationError;
                }
            }

            throw new TypeError(`Unsupported value at ${location}`);
        });
        records.push(fields.join(csvSeparator));
    }

    return (useBOM ? "\uFEFF" : "") + records.join("\r\n");
}
