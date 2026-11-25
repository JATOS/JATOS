# jatos.js build

This directory contains the source and standalone esbuild configuration for
the browser library and its HTTP and heartbeat workers.

The main sources are `src/index.js`, the standard-build entry point
`src/with-jquery.js`, and the workers in `src/workers/`. Do not edit the generated files directly:

- `../public/javascripts/jatos.js`
- `../public/javascripts/jatos.min.js`
- `../public/javascripts/jatos.slim.js`
- `../public/javascripts/jatos.slim.min.js`
- `../public/javascripts/http-loop-worker.js`
- `../public/javascripts/heartbeat.js`
- `../public/javascripts/heartbeat.min.js`

Install the pinned build and test dependencies (Node.js 18 or newer):

```bash
npm ci
```

Build the classic browser bundle:

```bash
npm run build
```

Check that the committed bundle matches its source:

```bash
npm run check
```

The generated browser-library files remain single classic scripts and preserve the global
`var jatos` and `window.jatos` API used by existing studies. `jatos.min.js` is
the minified production variant. `http-loop-worker.js` and `heartbeat.js` remain separately loaded
classic Web Workers. `heartbeat.min.js` is also generated from the same source;
the runtime continues to load `heartbeat.js`. Build JavaScript separately with
`npm run build` from this directory; sbt does not run npm.

Public `jatos` methods are assigned explicitly in `src/index.js`. Stateful
modules return their methods from factories; modules with internal lifecycle
methods keep the public methods under `api`. Keep shared state inside those
factories and expose each public method explicitly in the entry point. The
standard-only `jatos.jQuery` compatibility API is assigned in `src/with-jquery.js`.


Run the unit, extraction smoke, and browser-bundle integration tests:

```bash
npm test
```

The test dependencies are development-only and are not included in browser bundles.

JatosPromise uses a private jQuery 3.7.1 Deferred extraction in
`src/vendor/jquery-deferred.js`, with its MIT license alongside it. It is bundled
into both standard and slim builds. See [vendor notes](src/vendor/README.md) for
its adaptations and update procedure.

The standard builds bundle jQuery 3.7.1 as the deprecated `jatos.jQuery`
compatibility API, available immediately and before `onLoad`. They do not modify
page globals `$` or `jQuery`, and preserve the historical AJAX cache setting.

Use `jatos.slim.js` or `jatos.slim.min.js` for studies that do not use
`jatos.jQuery`. Slim builds omit full jQuery and the `jatos.jQuery` property;
all other JATOS APIs, the private Deferred, and JSON Patch remain included.
Both variants work via study-relative URLs and `/assets/javascripts/`.
Neither variant downloads startup scripts. GUI jQuery is unaffected.

JSON Patch is imported from the pinned `fast-json-patch@3.1.1` npm package and
bundled into both browser variants. No additional JSON Patch script or global is
needed. Its MIT notice is preserved in the bundles.

## ID helper

Call `jatos.addJatosIds(obj = {})` after `jatos.onLoad` to add the current study,
batch, component, worker, study-result, component-result, and group IDs, along
with study code, titles, worker type (`workerType`), and component position. It also adds `studyUuid`,
`componentUuid`, and `studyResultUuid`. The helper updates and returns the same
object, overwriting matching fields; without an argument it creates a new object.
Unavailable metadata (such as group IDs outside a group) remains `undefined`.

## CSV helper

`jatos.convertToCSV(rows, separator = ",", useBOM = false)` is available in
standard and slim builds, including before `onLoad`. It returns delimited text
with headers collected from all rows in first-seen order and CRLF between records:

```javascript
const csv = jatos.convertToCSV([{participant: "p1", score: 42}]);
// participant,score\r\np1,42
```

Objects and arrays in cells are serialized as JSON. Null, undefined, missing,
and empty-string cells become quoted empty fields. Headers and values are escaped
as needed; multi-character separators cause all fields to be quoted. Set `useBOM`
to `true` to prepend a Unicode BOM. An empty array returns an empty string.
Invalid arguments, unsupported cell types, and JSON serialization failures throw
`TypeError`. This helper only returns text; it does not submit or download results.

## Real study run check

The test `studyrun.StudyRunIntegrationTest` runs that study in Chromium, Firefox and WebKit
with the current standard and minified clients, plus abort scenarios (nine runs in total).
It verifies persisted results afterward.
It needs an installed Playwright package and all three browser binaries; it does
not download them. From the repository root:

```bash
JATOS_PLAYWRIGHT_MODULE=/path/to/node_modules/@playwright/test \
sbt 'testOnly gui.StudyImportExportIntegrationTest studyrun.StudyRunIntegrationTest'
```

Without this environment variable, only the optional browser test is skipped.
The legacy archive round-trip test still runs.
