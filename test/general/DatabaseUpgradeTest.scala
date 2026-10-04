package general

import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.{Rule, Test}
import org.junit.rules.TemporaryFolder
import play.db.{Database, Databases}
import play.db.evolutions.{Evolution, Evolutions}
import play.inject.guice.GuiceApplicationBuilder
import play.test.Helpers

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.UUID
import scala.jdk.CollectionConverters._

class DatabaseUpgradeTest {
  private val temp = new TemporaryFolder()
  @Rule def temporaryFolder: TemporaryFolder = temp

  @Test def releasedEvolutionFilesAreImmutable(): Unit = {
    val manifest = getClass.getResourceAsStream("/database/v3.11.3-evolutions.sha256")
    val lines = try new String(manifest.readAllBytes(), UTF_8).linesIterator.toList finally manifest.close()
    lines.filterNot(_.startsWith("#")).foreach { line =>
      val Array(revision, expected) = line.split(" ")
      val stream = getClass.getResourceAsStream(s"/evolutions/default/$revision.sql")
      val bytes = try stream.readAllBytes() finally stream.close()
      val actual = MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString
      assertEquals(s"Released evolution $revision must not change; add a new evolution instead", expected, actual)
    }
  }

  @Test def h2UpgradePreservesDataAndRestarts(): Unit = {
    val url = "jdbc:h2:" + temp.getRoot.toPath.resolve("upgrade") +
      ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;NON_KEYWORDS=USER"
    checkUpgrade("org.h2.Driver", url, "sa", "")
  }

  /** Opt-in: a dedicated test server only. Creates and drops a uniquely named database.
    * JATOS_TEST_MYSQL_OPTIONS supplies JDBC parameters (MariaDB: useInformationSchema=false).
    */
  @Test def mysqlUpgradePreservesDataAndRestarts(): Unit = {
    val server = sys.env.getOrElse("JATOS_TEST_MYSQL_SERVER", "")
    assumeTrue("Set JATOS_TEST_MYSQL_SERVER to a disposable server URL ending in /", server.nonEmpty)
    require(server.endsWith("/"), "Test server URL must end in / without a database name")
    val user = sys.env.getOrElse("JATOS_TEST_MYSQL_USER", "root")
    val password = sys.env.getOrElse("JATOS_TEST_MYSQL_PASSWORD", "")
    val options = sys.env.get("JATOS_TEST_MYSQL_OPTIONS").filter(_.nonEmpty).map("?" + _).getOrElse("")
    val name = "jatos_upgrade_" + UUID.randomUUID().toString.replace("-", "")
    val c = DriverManager.getConnection(server, user, password)
    try {
      val s = c.createStatement()
      try {
        s.execute(s"CREATE DATABASE `$name`")
        try checkUpgrade("com.mysql.cj.jdbc.Driver", server + name + options, user, password)
        finally s.execute(s"DROP DATABASE `$name`")
      } finally s.close()
    } finally c.close()
  }

  private def checkUpgrade(driver: String, url: String, user: String, password: String): Unit = {
    def database(): Database = Databases.createFrom(driver, url, Map[String, Object]("username" -> user, "password" -> password).asJava)
    val db = database()
    try {
      val released = Evolutions.fromClassLoader(getClass.getClassLoader).evolutions("default")
        .filter(_.revision <= 24).map(e => new Evolution(e.revision, e.sql_up, e.sql_down))
      assertEquals(24, released.size)
      Evolutions.applyEvolutions(db, Evolutions.forDefault(released.toList: _*))
      sql(db, "INSERT INTO Study (id,uuid,title,locked,groupStudy,linearStudy,active) VALUES (100,'upgrade-study','Grüße',0,0,0,1)")
      sql(db, "INSERT INTO Batch (id,uuid,title,active,study_id,batchSessionVersion) VALUES (100,'upgrade-batch','batch',1,100,0)")
      sql(db, "INSERT INTO Component (id,uuid,title,active,reloadable,study_id,htmlFilePath) VALUES (100,'upgrade-component','component',1,0,100,'index.html')")
      sql(db, "INSERT INTO Worker (id,workerType) VALUES (100,'PersonalSingle')")
      sql(db, "INSERT INTO BatchWorkerMap (batch_id,worker_id) VALUES (100,100)")
      sql(db, "INSERT INTO StudyResult (id,uuid,study_id,batch_id,worker_id,quotaReached) VALUES (100,'upgrade-result',100,100,100,0)")
      sql(db, "INSERT INTO ComponentResult (id,component_id,studyResult_id,data,quotaReached) VALUES (100,100,100,'legacy result',0)")
      sql(db, "INSERT INTO ApiToken (id,tokenHash,name,user_username,creationDate,expires,active) VALUES (100,'test-hash','test-token','admin',CURRENT_TIMESTAMP,0,1)")
    } finally db.shutdown()

    // Use actual application startup for evolutions, charset repair, data migrations and JPA.
    for (_ <- 1 to 2) {
      val root = temp.getRoot.getAbsolutePath
      val app = new GuiceApplicationBuilder()
        .configure("db.default.driver", driver).configure("db.default.url", url)
        .configure("db.default.username", user).configure("db.default.password", password)
        .configure("jatos.studyAssetsRootPath", root + "/assets")
        .configure("jatos.studyLogs.path", root + "/study-logs")
        .configure("jatos.resultUploads.path", root + "/uploads")
        .configure("jatos.logs.path", root + "/logs").configure("jatos.tmpPath", root + "/tmp")
        .build()
      try {
        Helpers.start(app)
        val current = app.injector().instanceOf(classOf[Database])
        assertEquals("25", scalar(current, "SELECT MAX(id) FROM play_evolutions"))
        assertEquals("0", scalar(current, "SELECT COUNT(*) FROM play_evolutions WHERE state <> 'applied'"))
        assertEquals("Grüße", scalar(current, "SELECT title FROM Study WHERE id=100"))
        assertEquals("legacy result", scalar(current, "SELECT data FROM ComponentResult WHERE id=100"))
        assertEquals("13", scalar(current, "SELECT dataSize FROM ComponentResult WHERE id=100"))
        assertEquals("1", scalar(current, "SELECT COUNT(*) FROM StudyLink WHERE batch_id=100 AND worker_id=100"))
        assertEquals("test-hash", scalar(current, "SELECT tokenHash FROM ApiToken WHERE id=100"))
        assertEquals("21232f297a57a5a743894a0e4a801fc3", scalar(current, "SELECT passwordHash FROM User WHERE username='admin'"))
      } finally Helpers.stop(app)
    }
    val upgraded = database()
    try {
      // New cascading deletes remove dependents without deleting workers shared by other batches.
      sql(upgraded, "DELETE FROM Study WHERE id=100")
      for (table <- Seq("Batch", "Component", "StudyResult", "ComponentResult"))
        assertEquals(table, "0", scalar(upgraded, s"SELECT COUNT(*) FROM `$table` WHERE id=100"))
      assertEquals("0", scalar(upgraded, "SELECT COUNT(*) FROM StudyLink WHERE batch_id=100"))
      assertEquals("1", scalar(upgraded, "SELECT COUNT(*) FROM Worker WHERE id=100"))
      assertEquals("1", scalar(upgraded, "SELECT COUNT(*) FROM ApiToken WHERE id=100"))
    } finally upgraded.shutdown()
  }

  private def sql(db: Database, statement: String): Unit = {
    val c = db.getConnection()
    try { val s = c.createStatement(); try s.execute(statement) finally s.close() } finally c.close()
  }
  private def scalar(db: Database, statement: String): String = {
    val c = db.getConnection()
    try {
      val s = c.createStatement()
      try { val r = s.executeQuery(statement); try { assertTrue(r.next()); r.getString(1) } finally r.close() }
      finally s.close()
    } finally c.close()
  }
}
