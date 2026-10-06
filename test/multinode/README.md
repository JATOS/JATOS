# Local multi-node integration tests

Run from a checkout of the branch you want to test:

```bash
JATOS_PLAYWRIGHT_MODULE=/absolute/path/to/node_modules/@playwright/test \
  test/multinode/run.sh
```

Prerequisites: Docker with Compose v2 or later supporting `up --wait`, Bash,
Node.js, Python 3, sbt and a JDK supported by this branch. An installed Playwright
package and its Chromium browser are required. The test does not install browser
dependencies. Docker needs the MySQL 8.4, Nginx 1.28 Alpine and Temurin 25 images
cached locally or network access to pull them.

The runner builds the **current checkout** using `sbt 'Assets / clean' 'Docker / stage'`
and the production `deploy/Dockerfile`. It does not run automatically with `sbt test`.
For a development rerun with an image you have already built from the current code:

```bash
JATOS_PLAYWRIGHT_MODULE=/absolute/path/to/node_modules/@playwright/test \
  test/multinode/run.sh --image my-current-jatos-test-image
```

Using `--image` deliberately skips the build; the caller must ensure it is current.
That image is not removed by cleanup. No image is pushed and no Git commit is made.

## Isolation

Each run creates a unique `jatos-it-...` Compose project, fresh MySQL and shared-data
volumes, a private Docker network, two JATOS nodes, and Nginx. Published ports are
chosen from free ports at startup, fixed for the run, and bound to `127.0.0.1`. It does not reuse the existing
`deploy/docker-compose-multi-node.yaml` project's containers, ports or volumes.
Only test credentials are used. The shared volume holds study assets and uploaded
results; a shared database alone would not make these files accessible on both nodes.

Both fixed test nodes are Pekko seeds. This permits either to rejoin through the
survivor when it restarts. The deployment example also uses two fixed seeds, plus
optional scalable replicas. These tests exercise the two-node topology; they do not
cover scaling additional replicas. The test-only Nginx header identifies the upstream
used for proxy checks.
The test proxy also uses `proxy_connect_timeout 2s` to bound connection attempts to
stopped containers while Docker DNS converges. The deployment example retains its
default timeout; its failover latency is therefore not certified by this test.

## Scenarios

- Import a real study using the authenticated API, enable group mode, and create
  three distinct participant links.
- Connect participants explicitly to different nodes. Confirm actual cross-node
  batch publication before proceeding; HTTP health alone is not cluster readiness.
- Check shared batch/group state and session acknowledgements, ordered direct
  messages, recipient isolation, broadcasts, and concurrent batch updates.
  Explicit contention conflicts use bounded retries; arbitrary failures do not.
- Upload on one node and download through the other.
- Stop/start the worker service and then the original seed service. Verify the
  survivor remains reachable, participant channels recover, group identities and
  saved sessions remain stable, and messaging works again.
- Kill the worker with SIGKILL, start it again, and repeat recovery checks.
- Confirm Nginx routes to both nodes after recovery. Run an additional participant
  through Nginx, prove the restarted survivor can open a batch channel using a
  separate readiness participant, then stop the original node. Verify reconnection
  and completion through the survivor while the original node remains stopped.
- Export and check results written before and appended after restarts, verify the
  uploaded file still exists, leave the group, finish studies and check active
  membership cleanup. Delete the study and verify shared asset cleanup on both nodes.

The scenario runner has bounded polling and an overall watchdog. It tests delivery
and ordering of messages sent after recovery, not exactly-once delivery of messages
in flight during a failure. Network partitions, simultaneous loss of both nodes,
production-scale load and long-duration leak testing are not covered.

## Deletion regression coverage

The study DELETE assertion previously exposed a MySQL foreign-key failure involving
historical group memberships. Study and batch deletion now explicitly remove and
flush study/component results before deleting their parents. Filesystem cleanup
runs after the owning transaction commits. The full multi-node scenario, including
study deletion and shared asset/upload cleanup, passes with this fix.

Additional [single-node deletion tests](../gui/DELETION_TESTS.md) cover active and
historical group memberships, sibling data, rollback and cleanup failures on H2
and MySQL.

## Diagnostics and cleanup

Logs and `summary.json` are retained in `.cache-tests/multi-node/<project>/`, including
build/startup output, test output, container logs/state, and cleanup results. Result
archives contain only the synthetic test participants' data. Test tokens are not
written to the summary. This ignored directory survives sbt's clean step.

The runner traps success, failure, Ctrl-C and termination, collects logs, and runs
`docker compose down --volumes --remove-orphans` for **its own project only**. It also
removes an image it built itself and checks for leftover project resources. It never
runs Docker prune or removes unrelated resources. A host crash or SIGKILL of the
runner cannot execute a shell trap; the printed/logged project name identifies the
resources for manual cleanup:

```bash
docker compose --env-file .cache-tests/multi-node/<project>/compose.env \
  -p <exact-jatos-it-project-name> -f test/multinode/compose.yaml \
  down --volumes --remove-orphans
```
