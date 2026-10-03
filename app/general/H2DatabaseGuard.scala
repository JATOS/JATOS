package general

import org.h2.engine.ConnectionInfo
import org.h2.store.fs.FileUtils

import java.nio.charset.StandardCharsets

private[general] object H2DatabaseGuard {

  /**
   * Inspect local H2 files without opening a database connection. JDBC metadata would report the
   * bundled engine version, not the version that created the file, and opening it may be too late.
   * Called during module loading, before Play evolutions or JPA can access the database.
   * Encrypted MVStore headers and remote H2 databases cannot be inspected by this file check.
   */
  def validate(url: String): Unit = {
    if (!url.startsWith("jdbc:h2:")) return

    // Only parse the database name: URL settings (in particular INIT) must never be executed here.
    val info = new ConnectionInfo(url.stripPrefix("jdbc:h2:").takeWhile(_ != ';'))
    if (!info.isPersistent || info.isRemote) return

    val name = info.getName
    def rejectLegacy(): Nothing = throw new IllegalStateException(
      "JATOS startup stopped: the embedded database was created by H2 1.x. " +
        "This release requires H2 2.x. No database migrations have been run. " +
        "Back up the database, export it using the old H2 version, and import it into a fresh " +
        "H2 2.x database before starting JATOS again.")

    // PageStore files are no longer supported. H2 2 would otherwise create a new .mv.db beside them.
    if (FileUtils.exists(name + ".h2.db")) rejectLegacy()

    val mvFile = name + ".mv.db"
    if (FileUtils.exists(mvFile)) {
      val input = FileUtils.newInputStream(mvFile)
      val headers = try input.readNBytes(8192) finally input.close()
      // MVStore has two 4096-byte header blocks. H2 1.x uses store format 1.
      // This is a legacy-format guard, not a general integrity check; H2 validates other formats.
      val format = "(?:^|,)format:([0-9a-fA-F]+)(?:,|$)".r
      for (offset <- Seq(0, 4096) if headers.length > offset) {
        val header = new String(headers, offset, math.min(4096, headers.length - offset),
          StandardCharsets.ISO_8859_1).takeWhile(c => c != '\n' && c != '\r')
        if (header.startsWith("H:2,")) {
          format.findFirstMatchIn(header).foreach { m =>
            if (java.lang.Long.parseLong(m.group(1), 16) < 2) rejectLegacy()
          }
        }
      }
    }
  }
}
