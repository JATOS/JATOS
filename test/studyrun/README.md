# Study run integration checks

These tests run an isolated JATOS test server with the H2 test database and assets
under `/tmp/jatos_tests`. They require Node.js, an installed `@playwright/test`
package, and its browser binaries; they do not install or download anything.
Without `JATOS_PLAYWRIGHT_MODULE`, JUnit skips the browser tests.

```bash
JATOS_PLAYWRIGHT_MODULE=/path/to/node_modules/@playwright/test \
sbt 'testOnly studyrun.StudyRunIntegrationTest studyrun.StudyRunReliabilityIntegrationTest'
```

`StudyRunIntegrationTest` runs the example study with standard and minified clients
and abort scenarios in Chromium, Firefox and WebKit.

`StudyRunReliabilityIntegrationTest` uses Chromium for six concurrent participants.
Each updates its own shared batch-session key, reconnects twice, submits 1 MiB of
result data across an offline/online transition, appends ten records, disconnects
partway through a separate HTTP request body, and finishes. The Java test checks
saved result hashes, all six batch keys, and channel unregistration. Explicit
batch update conflicts are retried with bounded backoff, reflecting the server's
bounded compare-and-set retry policy.

The same test exports a study containing a 32 MiB incompressible asset and a combined
result archive containing the six participants' data. It validates archive contents
and cancels three downloads of each kind, checking temporary-file cleanup and
successful exports afterward. Logs are retained in temporary files and their paths
are printed. This is a bounded reliability regression, not a load/capacity or soak
benchmark. It does not test machine crashes or exactly-once delivery when an append
succeeds on the server but its response is lost.
