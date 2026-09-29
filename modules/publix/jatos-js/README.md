# jatos.js build

This directory contains the source and standalone esbuild configuration for
the browser library served as `jatos.js`.

The authoritative source is `src/index.js`. Do not edit the generated file at
`../public/javascripts/jatos.js` directly.

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

The generated file remains a single classic script and preserves the global
`var jatos` and `window.jatos` API used by existing studies. This build is not
yet integrated into sbt.
