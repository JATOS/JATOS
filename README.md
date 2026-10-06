This is JATOS - Just Another Tool for Online Studies.

More information about JATOS: [www.jatos.org/Whats-JATOS.html](http://www.jatos.org/Whats-JATOS.html)

This software is licensed under the [Apache 2 License](http://www.apache.org/licenses/LICENSE-2.0.html).


## Build from source

Run the following commands from the repository root. Install a JDK (Java 21 or
newer; development and release builds use Java 25) and sbt. The project pins sbt
1.10.11 in `project/build.properties`; sbt downloads the required Scala and Play
dependencies on the first build. Make sure `java` and `sbt` are on your `PATH`
and that `JAVA_HOME`, if set, points to the intended JDK.

```bash
sbt compile
sbt test
```

To build the production distribution directly with sbt:

```bash
sbt clean test dist
```

The distribution ZIP is written to `target/universal/`. For the release ZIPs,
including variants with bundled Java runtimes, use the release script below.

### JavaScript development

The generated `jatos.js` bundles are committed to the repository, so Node.js is
not required for a normal sbt build. When changing their sources, install Node.js
18 or newer and npm, then run:

```bash
cd modules/publix/jatos-js
npm ci
npm run build
npm test
npm run check
```

Return to the repository root before running sbt. Neither sbt nor the release
script runs npm automatically. See
[the jatos.js build instructions](modules/publix/jatos-js/README.md) for details.

### Multi-node integration tests

The opt-in [multi-node test runner](test/multinode/README.md) builds the current
checkout and exercises cross-node messaging, node restarts, and cleanup in an
isolated Docker environment. It requires Docker Compose and installed Playwright
Chromium; it is separate from `sbt test`.

### Database deletion integration tests

[Study and batch deletion tests](test/gui/DELETION_TESTS.md) exercise the real schema,
group memberships, cascading deletion, and filesystem cleanup on commit or rollback.
H2 checks run with `sbt test`; MySQL checks are opt-in against a disposable server.

## Run in development mode

From the repository root:

```bash
sbt run
```

Open [http://localhost:9000](http://localhost:9000). Play runs in development mode
and recompiles/reloads changed application code on subsequent requests. Press
Ctrl+D to stop the server and return to sbt; use `exit` to leave an interactive
sbt session.

By default, JATOS uses `conf/application.conf`, with an embedded H2 database under
`database/` and study assets under `study_assets_root/`, relative to the repository
root. Use a separate development database and data directories from your
production installation.

To load the development overrides in `conf/development.conf` explicitly:

```bash
sbt -Dconfig.resource=development.conf run
```

That file includes `application.conf` and contains example/local settings; review
its paths and authentication settings before using it. To use a different port:

```bash
sbt "run 9001"
```

## Build production release ZIPs

`deploy/build-release.sh` runs `sbt clean test dist`, repackages the distribution,
checks the ZIP archives, and generates SHA-256 checksums. Run it from the repository
root in Bash with `java`, `sbt`, `git`, `curl`, `tar`, `unzip`, `zip`, and
`sha256sum` available on your `PATH`.

Set the release version in `VERSION` (without a leading `v`) and commit that file
before running the script. For a release build, use a clean checkout of the
intended release commit.

To build only the production ZIP without bundled Java:

```bash
bash deploy/build-release.sh --skip-java-bundles
```

This produces `target/release/jatos.zip` and `target/release/SHA256SUMS`.
The plain ZIP requires Java 21 or newer on the target machine.

To also build all bundled-Java variants:

```bash
bash deploy/build-release.sh
```

With the script's current Java 25 configuration, `target/release/` contains:

- `jatos.zip`
- `jatos_linux_java25.zip`
- `jatos_win_java25.zip`
- `jatos_mac_x64_java25.zip`
- `jatos_mac_aarch64_java25.zip`
- `SHA256SUMS`

The script downloads Eclipse Temurin JREs. Set `JAVA_VERSION` near the top of the
script to pin a particular runtime release; an empty value selects the latest
release for `JAVA_MAJOR`. Downloads are cached in `target/release-jres/` when
retained between builds; remove that cache when changing the runtime version.
The script replaces `target/release/` on each successful build.

Additional options are available with:

```bash
bash deploy/build-release.sh --help
```

- `--skip-clean`: retain existing build outputs.
- `--skip-tests`: omit the sbt tests.
- `--push-docker`: build and push Docker images; requires Docker authentication
  and the `jatos-builder` buildx builder configured as described in the script.
- `--docker-tag-latest`: also push the `latest` tag when using `--push-docker`.
- `--github-release`: create a draft GitHub release and upload the generated
  archives and checksums; requires the authenticated GitHub CLI (`gh`). It uses
  [deploy/RELEASE_NOTES_TEMPLATE.md](deploy/RELEASE_NOTES_TEMPLATE.md) as the release
  body, so fill in the template for the release before using this option.

Without publishing options, the script only builds local release artifacts.
