# jQuery Deferred extraction

`jquery-deferred.js` contains Deferred, Callbacks, and their required helpers
from the npm `jquery@3.7.1` package, corresponding to
https://github.com/jquery/jquery/tree/3.7.1.

## Adaptations

- Removes AMD wrappers and uses a private namespace with an ES module export.
- Uses `globalThis.setTimeout` instead of `window.setTimeout` for browser and Node use.
- Omits `when`, its `adoptValue` helper, and the optional exception-hook installation.
- Preserves the upstream Deferred and Callbacks algorithms.

`../jatos-promise.js` provides the internal adapter. Both standard and slim builds
include this extraction. Standard builds also bundle full jQuery solely for the
deprecated `jatos.jQuery` compatibility API.

## License and updates

The upstream MIT license is preserved in `jquery-deferred.LICENSE.txt` and the
module's legal comment, which is retained in generated browser bundles.

When updating, compare the upstream Deferred, Callbacks, and helper sources with
this extraction, preserve the adaptations above, and update the version and
license as needed. Git history records previous versions.

From `modules/publix/jatos-js`, run `npm test`, `npm run build`, and `npm run check`
to validate the adapter, channel behavior, and standard/slim browser bundles.
