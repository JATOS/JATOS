package migrations.common

import general.common.Common
import play.api.Logger
import play.api.db._

import java.sql.{SQLException, Statement}
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ListBuffer

/**
 * Fixes the charset and collation of MySQL tables to the proper UTF-8 charset 'utf8mb4' (instead of 'utf8' as it is
 * defined in the evolution scripts). Called during start-up.
 */
@Singleton
class MySQLCharsetFix @Inject()(db: Database, jatosMigrations: JatosMigrations) {

  private val logger = Logger(this.getClass)

  private case class ForeignKey(constraintName: String,
                                tableName: String,
                                columnName: String,
                                referencedTableName: String,
                                referencedColumnName: String,
                                updateRule: String,
                                deleteRule: String)

  def run(): Unit = {
    if (!Common.usesMysql()) return

    try jatosMigrations.start(() => this.fix())
    catch {
      case e: Exception =>
        throw new RuntimeException("MySQLCharsetFix failed", e)
    }
  }

  private def fix(): Unit = {
    val connection = db.getConnection()
    try {
      val statement = connection.createStatement()
      val databaseName = connection.getCatalog
      val tablesToConvert = getTablesNeedingConversion(databaseName, statement)

      if (tablesToConvert.nonEmpty) {
        val foreignKeys = getForeignKeys(databaseName, statement)

        // Disable FK checks
        statement.execute("SET FOREIGN_KEY_CHECKS=0")

        // 1. Drop foreign keys (required by MariaDB before altering column character sets)
        foreignKeys.foreach { fk =>
          try {
            statement.executeUpdate(s"ALTER TABLE `${fk.tableName}` DROP FOREIGN KEY `${fk.constraintName}`")
          } catch {
            case e: SQLException =>
              logger.warn(s"Could not drop foreign key ${fk.constraintName} on ${fk.tableName}: ${e.getMessage}")
          }
        }

        // 2. Convert tables to utf8mb4
        tablesToConvert.foreach { tableName =>
          statement.executeUpdate(s"ALTER TABLE `$tableName` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        }

        // 3. Re-create foreign keys
        foreignKeys.foreach { fk =>
          val onUpdate = if (fk.updateRule.nonEmpty && fk.updateRule != "RESTRICT" && fk.updateRule != "NO ACTION") s" ON UPDATE ${fk.updateRule}" else ""
          val onDelete = if (fk.deleteRule.nonEmpty && fk.deleteRule != "RESTRICT" && fk.deleteRule != "NO ACTION") s" ON DELETE ${fk.deleteRule}" else ""
          try {
            statement.executeUpdate(
              s"ALTER TABLE `${fk.tableName}` ADD CONSTRAINT `${fk.constraintName}` " +
                s"FOREIGN KEY (`${fk.columnName}`) REFERENCES `${fk.referencedTableName}` (`${fk.referencedColumnName}`)$onUpdate$onDelete"
            )
          } catch {
            case e: SQLException =>
              logger.warn(s"Could not recreate foreign key ${fk.constraintName} on ${fk.tableName}: ${e.getMessage}")
          }
        }

        // Re-enable FK checks
        statement.execute("SET FOREIGN_KEY_CHECKS=1")

        logger.info("Converted tables in MySQL/MariaDB database to CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
      }
    } catch {
      case e: SQLException =>
        throw new RuntimeException("Error during converting tables in MySQL/MariaDB to CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", e)
    } finally {
      connection.close()
    }
  }

  private def getTablesNeedingConversion(databaseName: String, statement: Statement): List[String] = {
    val tables = new ListBuffer[String]()

    for (tableName <- getAllTableNames(databaseName, statement)) {
      val result = statement.executeQuery(
        s"SELECT CCSA.character_set_name FROM information_schema.`TABLES` T, " +
          s"information_schema.`COLLATION_CHARACTER_SET_APPLICABILITY` CCSA " +
          s"WHERE CCSA.collation_name = T.table_collation " +
          s"AND T.table_schema = '$databaseName' " +
          s"AND T.table_name = '$tableName'"
      )
      if (result.next()) {
        val charSet = result.getString("character_set_name")
        if (charSet.toLowerCase.contains("utf8") && charSet.toLowerCase != "utf8mb4"
          && !tableName.contains("play_evolutions")) {
          tables += tableName
        }
      }
      result.close()
    }

    tables.toList
  }

  private def getForeignKeys(databaseName: String, statement: Statement): List[ForeignKey] = {
    val foreignKeys = new ListBuffer[ForeignKey]()
    val query =
      s"SELECT kcu.CONSTRAINT_NAME, kcu.TABLE_NAME, kcu.COLUMN_NAME, " +
        s"kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME, " +
        s"rc.UPDATE_RULE, rc.DELETE_RULE " +
        s"FROM information_schema.KEY_COLUMN_USAGE kcu " +
        s"JOIN information_schema.REFERENTIAL_CONSTRAINTS rc " +
        s"ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME AND kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA " +
        s"WHERE kcu.TABLE_SCHEMA = '$databaseName' AND kcu.REFERENCED_TABLE_NAME IS NOT NULL"

    val result = statement.executeQuery(query)
    while (result.next()) {
      foreignKeys += ForeignKey(
        constraintName = result.getString("CONSTRAINT_NAME"),
        tableName = result.getString("TABLE_NAME"),
        columnName = result.getString("COLUMN_NAME"),
        referencedTableName = result.getString("REFERENCED_TABLE_NAME"),
        referencedColumnName = result.getString("REFERENCED_COLUMN_NAME"),
        updateRule = Option(result.getString("UPDATE_RULE")).getOrElse(""),
        deleteRule = Option(result.getString("DELETE_RULE")).getOrElse("")
      )
    }
    result.close()
    foreignKeys.toList
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
