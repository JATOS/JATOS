# jatos.js build

This directory contains the source and standalone esbuild configuration for
the browser library and its HTTP worker.

The authoritative sources are `src/index.js` and
`src/workers/http-loop-worker.js`. Do not edit the generated files directly:

- `../public/javascripts/jatos.js`
- `../public/javascripts/jatos.min.js`
- `../public/javascripts/http-loop-worker.js`

Install the pinned build dependency once:

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
the minified production variant. `http-loop-worker.js` remains a separately loaded
classic Web Worker. This build is not yet integrated into sbt.
