Try out the new version on [cortex.jatos.org](https://cortex.jatos.org/).

---

## Changed

* Fixes
* Features
* Changes
 
---

## Upgrading

### Old H2 1.x databases require an explicit migration

**Embedded H2 1.x database** files cannot be opened by H2 2.x. For an H2 upgrade,
export the stopped database using the old H2 version and import
that SQL into a separate database using the new H2 version. Keep the original files
and validate the converted copy before deploying it. See the
[H2 migration instructions](https://h2database.com/html/migration.html).
If you have a custom H2 JDBC URL in JATOS' config: remove `SELECT_FOR_UPDATE_MVCC=FALSE`
and include `NON_KEYWORDS=USER`

**MySQL and MariaDB do not need an H2 conversion.**

### Java 21 or newer required

**GUI auto-update can install bundled Java automatically.** On supported single-node
Linux and macOS installations, if your running Java is too old, the updater downloads
JATOS bundled with Java 25 and uses that runtime after restarting. It does not update
system Java. If you **do not** (want to) use the GUI auto-update, you need to install
Java 21 or newer manually.

**Always back up and test a migration before deployment.**

---

Previous releases: [github.com/JATOS/JATOS/releases](https://github.com/JATOS/JATOS/releases)

---

## Which variant do I need?

- If you have **Java >= 21** already installed (all OS): _jatos.zip_
- **Not Java >= 21** installed: Choose the package for your operating system. These packages
  include [Eclipse Temurin](https://adoptium.net/temurin/releases):
    - jatos_win_java25.zip — Windows
    - jatos_mac_aarch64_java25.zip — macOS (Apple Silicon)
    - jatos_mac_x64_java25.zip — macOS (Intel)
    - jatos_linux_java25.zip — Linux
- If you prefer **Docker**: [hub.docker.com/r/jatos/jatos](https://hub.docker.com/r/jatos/jatos/tags?page=1)
