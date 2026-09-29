# jatos.js build

This directory contains the source and standalone esbuild configuration for
the browser library served as `jatos.js` and `jatos.min.js`.

The authoritative source is `src/index.js`. Do not edit the generated files at
`../public/javascripts/jatos.js` and `../public/javascripts/jatos.min.js` directly.

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

Both generated files remain single classic scripts and preserve the global
`var jatos` and `window.jatos` API used by existing studies. `jatos.min.js` is
the minified production variant. This build is not yet integrated into sbt.
