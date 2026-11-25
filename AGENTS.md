# Working on JATOS

JATOS runs browser-based studies and collects participant results. Prioritize reliable study execution, data integrity,
and compatibility with existing studies.

## Repository layout

- `modules/common`: shared models, DAOs, utilities, and database evolutions.
- `modules/gui`: researcher/admin interface, controllers, and templates.
- `modules/publix`: participant-facing study execution.
- `modules/session`: batch and group sessions.
- `modules/publix/jatos-js`: sources, build, and tests for jatos.js.
- `conf`: application configuration and root routes.
- `test`: application-level and integration tests.
- `deploy`: packaging and deployment scripts.
- `jatos-api.yaml`: HTTP OpenAPI specification.

Follow the patterns in the affected module. Keep shared functionality in common and avoid introducing circular module
dependencies.

## Working conventions

- Inspect the working tree before editing. Preserve unrelated changes.
- Keep it simple. Prefer human-readable concise code.
- Keep changes focused on the requested task.
- Follow surrounding code style; avoid unrelated formatting or refactoring.
- Read relevant build files and test documentation before changing tooling.
- Treat build files and lockfiles as the source of truth for versions.
- Do not edit vendored libraries unless the task requires it.

## Compatibility and data integrity

- Preserve existing jatos.js APIs and study behavior unless a breaking change is explicitly requested.
- Consider compatibility of study archives, API responses, and stored data.
- Keep `jatos-api.yaml` aligned with HTTP API changes.
- Use a new database evolution for schema changes; do not rewrite evolutions already released.
- Consider both H2, MariaDB, and MySQL when changing persistence or SQL.
- Preserve transaction boundaries and verify filesystem cleanup behavior when changing deletion, imports, exports, or
  result handling.
- Prefer `forReadOnly` for read-only database access. For reads requiring transactional consistency, use an
  explicit transaction with appropriate isolation instead.
- Preserve authorization checks and isolation between users, studies, batches, groups, and participants.
- Do not log credentials, API tokens, or participant result contents.
- Use isolated test data and services for validation.

## JavaScript client

Before changing jatos.js or its workers, read
`modules/publix/jatos-js/README.md` and follow its build and validation instructions.

Edit source files, regenerate the affected bundles, and include the generated outputs in the change. Preserve
compatibility with existing studies unless explicitly asked otherwise.

## Build and validation

Run sbt commands from the repository root:

- `sbt compile` — compile the application.
- `sbt 'common / test'` — test the common module.
- `sbt 'gui / test'` — test the GUI module.
- `sbt 'publix / test'` — test the study-execution module.
- `sbt 'session / test'` — test the session module.
- `sbt 'testOnly package.TestClass'` — run a selected root test.
- `sbt test` — run the broader JVM test suite.

Start with tests covering the affected behavior. Add regression coverage for substantive bug fixes and broaden
validation for cross-module changes.

For browser study execution, read `test/studyrun/README.md`. Browser tests require `JATOS_PLAYWRIGHT_MODULE` and
installed browsers; a skipped browser test is not a successful browser check.

For clustering and failover, read `test/multinode/README.md`. These checks run separately from `sbt test` and require
Docker and additional prerequisites.

Do not claim checks passed unless they actually ran successfully. Report failures, skipped checks, and missing
prerequisites.

## Completing work

- Review the diff for unintended changes.
- Update relevant documentation when behavior or configuration changes.
- Summarize the resulting behavior, validation, and remaining limitations.
- For pull requests, explain the problem and solution and link the relevant issue when available; follow
  `CONTRIBUTING.md`.
