package general

import general.common.Common
import play.api.Logger
import play.api.db._

import java.sql.{SQLException, Statement}
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ListBuffer

/**
  * Fixes the charset and collation of MySQL tables to the proper UTF-8 charset 'utf8mb4' (instead of 'utf8' as it is
  * defined in the evolution scripts). Called during start-up.
  *
  * @author Kristian Lange
  */
@Singleton
class MySQLCharsetFix @Inject()(db: Database) {

  private val logger = Logger(this.getClass)

  def run(): Unit = {
    if (!Common.usesMysql()) return

    val connection = db.getConnection()
    try {
      val statement = connection.createStatement()
      val databaseName = connection.getCatalog
      val batch = generateMysqlCharsetConversionStatements(databaseName, statement)

      if (batch.nonEmpty) {
        // Turn off foreign key checks
        statement.addBatch("SET FOREIGN_KEY_CHECKS=0")
        batch.foreach {
          statement.addBatch
        }
        // Turn back on foreign key checks
        statement.addBatch("SET FOREIGN_KEY_CHECKS=1")

        statement.executeBatch()
        logger.info("Converted tables in MySQL database to CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
      }
    } catch {
      case e: SQLException =>
        logger.error("Error during converting tables in MySQL to CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", e)
    } finally {
      connection.close()
    }
  }

  private def generateMysqlCharsetConversionStatements(databaseName: String, statement: Statement) = {
    val batch = new ListBuffer[String]()

    for (tableName <- getAllTableNames(databaseName, statement)) {
      // Get character set names from information_schema database
      val result = statement.executeQuery(s"SELECT CCSA.character_set_name FROM information_schema.`TABLES` T, " +
        s"information_schema.`COLLATION_CHARACTER_SET_APPLICABILITY` CCSA " +
        s"WHERE CCSA.collation_name = T.table_collation " +
        s"AND T.table_schema = '$databaseName' " +
        s"AND T.table_name = '$tableName'")
      if (result.next()) {
        val charSet = result.getString("character_set_name")
        if (charSet.toLowerCase.contains("utf8") && charSet.toLowerCase != "utf8mb4"
            && !tableName.contains("play_evolutions")) {
          batch += s"ALTER TABLE `$tableName` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        }
      }
      result.close()
    }

    batch.toList
  }

  private def getAllTableNames(databaseName: String, statement: Statement) = {
    val tableNames = ListBuffer[String]()
    val result = statement.executeQuery(s"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$databaseName'")
    while (result.next()) {
      tableNames += result.getString("TABLE_NAME")
    }
    tableNames.toList
  }
}
