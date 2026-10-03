package general

import org.junit.Assert._
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import play.api.inject.guice.GuiceApplicationBuilder

import java.nio.file.Files
import java.sql.DriverManager

class H2DatabaseGuardTest {

  @Rule def temporaryFolder: TemporaryFolder = temp
  private val temp = new TemporaryFolder()

  @Test
  def validateH2Database_rejectsLegacyFileBeforeInjectionWithoutChangingIt(): Unit = {
    val database = temp.getRoot.toPath.resolve("legacy.mv.db")
    val fixture = getClass.getResourceAsStream("/h2/legacy-1.4.197.mv.db")
    try Files.copy(fixture, database) finally fixture.close()
    val before = Files.readAllBytes(database)
    val error = assertThrows(classOf[play.api.PlayException], () =>
      new GuiceApplicationBuilder()
        .configure("db.default.url" -> ("jdbc:h2:" + temp.getRoot.toPath.resolve("legacy") + ";MODE=MYSQL"))
        .build())
    val causes = Iterator.iterate[Throwable](error)(_.getCause).takeWhile(_ != null).toSeq
    assertTrue(causes.exists(e => Option(e.getMessage).exists(_.contains("H2 1.x"))))
    assertTrue(causes.exists(e => Option(e.getMessage).exists(_.contains("No database migrations have been run"))))
    assertArrayEquals(before, Files.readAllBytes(database))
    assertEquals(Seq("legacy.mv.db"), temp.getRoot.list().toSeq)
  }

  @Test
  def validateH2Database_checksSecondHeaderWhenFirstIsDamaged(): Unit = {
    val database = temp.getRoot.toPath.resolve("legacy.mv.db")
    val fixture = getClass.getResourceAsStream("/h2/legacy-1.4.197.mv.db")
    val bytes = try fixture.readAllBytes() finally fixture.close()
    java.util.Arrays.fill(bytes, 0, 4096, 0.toByte)
    Files.write(database, bytes)
    assertThrows(classOf[IllegalStateException], () =>
      H2DatabaseGuard.validate("jdbc:h2:" + temp.getRoot.toPath.resolve("legacy")))
  }

  @Test
  def validateH2Database_rejectsPageStoreWithoutCreatingMvStore(): Unit = {
    val database = temp.newFile("legacy.h2.db")
    assertThrows(classOf[IllegalStateException], () =>
      H2DatabaseGuard.validate("jdbc:h2:file:" + database.getPath.stripSuffix(".h2.db")))
    assertEquals(Seq("legacy.h2.db"), temp.getRoot.list().toSeq)
  }

  @Test
  def validateH2Database_acceptsCurrentDatabaseWithoutChangingIt(): Unit = {
    val name = temp.getRoot.toPath.resolve("current")
    val connection = DriverManager.getConnection("jdbc:h2:" + name)
    connection.close()
    val file = temp.getRoot.toPath.resolve("current.mv.db")
    val before = Files.readAllBytes(file)
    H2DatabaseGuard.validate("jdbc:h2:file:" + name + ";MODE=MYSQL")
    assertArrayEquals(before, Files.readAllBytes(file))
  }

  @Test
  def validateH2Database_acceptsNewDatabaseWithoutCreatingFilesOrExecutingInit(): Unit = {
    H2DatabaseGuard.validate("jdbc:h2:" + temp.getRoot.toPath.resolve("new") +
      ";INIT=RUNSCRIPT FROM 'does-not-exist.sql'")
    assertEquals(0, temp.getRoot.list().length)
  }

  @Test
  def validateH2Database_skipsOtherDatabasesAndNonLocalH2(): Unit = {
    Seq("jdbc:mysql://localhost/jatos", "jdbc:mariadb://localhost/jatos", "jdbc:h2:mem:test",
      "jdbc:h2:tcp://localhost/~/jatos", "jdbc:h2:ssl://localhost/~/jatos")
      .foreach(H2DatabaseGuard.validate)
  }

}
