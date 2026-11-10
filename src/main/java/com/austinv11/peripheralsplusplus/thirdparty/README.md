# Third-party code

## `gtranslate`

`com/austinv11/peripheralsplusplus/thirdparty/gtranslate/**` is a vendored copy of the small "gtranslate" Java
library: a Google Translate / Google TTS HTTP wrapper (`Translator`, `Language`, `Audio`, plus its `parsing`/`text`
helpers). It is what the speaker peripheral uses for `speak`/`translate`.

**Provenance is not recorded in this repository.** The package was `com.gtranslate` before it was moved here, and
neither the original repository nor a licence header was ever added. It was relocated in the review pass described
in `../CODE_REVIEW.md` (section 6.19) for two reasons:

1. it was shipping under a package name that looks like a real Maven coordinate, which invites a split-package
   conflict with any other mod bundling the same library;
2. the mod jar was shipping third-party code with no attribution next to it.

If you are the author of this library, please open an issue so the licence and attribution can be corrected.

### Known limitations

* The endpoints are the long-retired `translate.google.com/translate_a/t?` and `translate_tts?` URLs, and neither
  sends an API key. In practice `speak`/`translate` are best-effort and usually fail; the speaker's `synthesize`
  (local FreeTTS) works offline.
* Every call opens a `URLConnection` with no timeout configured.
* This code is excluded from the coverage report and is not part of the refactor plan; replacing it with a
  maintained dependency (or dropping `speak` in favour of `synthesize`) is the recommended fix.
