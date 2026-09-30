# jQuery Deferred extraction

`jquery-deferred.js` vendors the Deferred and Callbacks implementations from
**jquery@3.7.1**, matching the version shipped in `public/javascripts`.
It is a private ES module, bundled by esbuild into the existing classic scripts.
Only `Deferred` is exported; neither `jQuery` nor `Deferred` is added to the page.
The internal adapter in `../jatos-promise.js` directly exports `createDeferred`,
`rejectedPromise`, and `isDeferredPending`. Internal consumers import these
helpers directly; there is no compatibility factory or separate promise state.

## Provenance and license

The source is the npm `jquery@3.7.1` archive, corresponding to
https://github.com/jquery/jquery/tree/3.7.1. The archive's `dist/jquery.min.js`
was verified byte-for-byte against the repository's bundled jQuery 3.7.1.

The upstream MIT license is preserved verbatim in `jquery-deferred.LICENSE.txt`
and included in the module's legal comment, retained in both browser bundles.

## Scope and adaptations

- Includes complete Callbacks and Deferred implementations and their required
  core/type helpers, keeping upstream algorithm bodies and comments.
- Removes AMD wrappers and supplies a private namespace and primitive helpers.
- Replaces `window.setTimeout` with `globalThis.setTimeout` so the same code can
  run in browser bundles and Node tests without a DOM.
- Excludes `when`, its `adoptValue` helper, DOM/AJAX modules, and the optional
  `deferred/exceptionHook.js` installation. This preserves JatosPromise's existing
  absence of diagnostic console warnings; rejection behavior is unchanged.

This does not remove the full jQuery dependency used elsewhere by initialization
and AJAX. It does not expose the private Deferred's debug hooks as a public API.

## Updating

Compare the existing module with the new upstream version's `src/callbacks.js`,
`src/deferred.js`, and the core/type helpers identified by comments in the code.
Review upstream changes and apply them directly, preserving the adaptations above.
Update the version, provenance, and upstream license as needed. Git history records
previous versions of the extraction.

From `modules/publix/jatos-js`, validate the adapter and browser integration and
rebuild the distributed bundles:

```bash
npm test
npm run build
npm run check
```

Normal builds use the checked-in source without downloading jQuery.
