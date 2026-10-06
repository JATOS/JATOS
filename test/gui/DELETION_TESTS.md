# Study and batch deletion regression tests

Run the real-schema H2 checks (also included in `sbt test`):

```bash
sbt 'testOnly gui.DeletionCascadeIntegrationTest'
```

The MySQL variant is opt-in. Point it at a **disposable test server**, with an
account allowed to create/drop databases:

```bash
JATOS_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:3307/ \
JATOS_TEST_MYSQL_USER=root \
JATOS_TEST_MYSQL_PASSWORD=test-password \
JATOS_TEST_MYSQL_OPTIONS='allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC' \
  sbt 'testOnly gui.DeletionCascadeIntegrationTest'
```

The URL ends in `/` with no database name or query parameters. The test creates a
unique `jatos_deletion_<uuid>` database, runs production application startup and
schema evolutions, and drops only that database in `finally`. Assets, uploads and
logs use JUnit temporary directories. Both variants run in single-node mode.

Coverage includes:

- Whole-study and whole-batch deletion with multiple active and historical group
  members, component results and study links.
- Preservation of sibling batches (when deleting one batch), unrelated studies,
  and workers shared with those studies.
- Real outer transactions around nested service calls: flushing child deletions
  must not remove files before commit.
- Rollback from an exception, rollback-only, or a failure during commit: rows,
  membership references, assets, uploads and study logs remain intact.
- Successful commit removes the intended rows and files and retires the study log.
- A filesystem cleanup failure does not undo the database commit or prevent later
  cleanup callbacks from running; it is logged for manual cleanup.
- On MySQL, a raw parent DELETE reproduces the original restrictive group-membership
  foreign-key failure. Service deletion then verifies the explicit ordering fix.

Earlier mocked service tests could not detect database cascade ordering. The
upgrade test exercised cascades without group memberships. These tests cover the
missing combination against the actual schema and database engine. The separate
[multi-node suite](../multinode/README.md) covers the authenticated study DELETE
through HTTP after real participant runs and node restarts.
