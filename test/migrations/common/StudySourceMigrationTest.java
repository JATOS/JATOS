package migrations.common;

import org.junit.Test;
import play.db.Database;
import play.db.Databases;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.Assert.*;

public class StudySourceMigrationTest {

    @Test
    public void enforcesExclusiveUniqueOwnersAndCascadesDeletion() throws Exception {
        Database db = Databases.createFrom("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try {
            sql(db, "CREATE TABLE Study (id bigint PRIMARY KEY)");
            sql(db, "CREATE TABLE `User` (username varchar(255) PRIMARY KEY)");
            sql(db, "INSERT INTO Study VALUES (1),(2)");
            sql(db, "INSERT INTO `User` VALUES ('alice')");
            try (var stream = getClass().getResourceAsStream("/evolutions/default/27.sql")) {
                assertNotNull(stream);
                String up = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        .split("# --- !Ups")[1].split("# --- !Downs")[0].replaceAll("(?m)^#.*$", "");
                for (String statement : up.split(";")) if (!statement.isBlank()) sql(db, statement);
            }
            sql(db, "INSERT INTO StudySource (study_id) VALUES (1),(2)");
            assertThrows(SQLException.class, () -> sql(db, "INSERT INTO StudySource (study_id) VALUES (1)"));
            assertThrows(SQLException.class, () -> credential(db, "NULL", "NULL"));
            assertThrows(SQLException.class, () -> credential(db, "1", "'alice'"));
            assertThrows(SQLException.class, () -> credential(db, "99", "NULL"));
            credential(db, "1", "NULL");
            credential(db, "NULL", "'alice'");
            assertThrows(SQLException.class, () -> credential(db, "1", "NULL"));
            assertThrows(SQLException.class, () -> credential(db, "NULL", "'alice'"));
            sql(db, "DELETE FROM Study WHERE id=1");
            assertEquals(1, count(db, "GitCredentials"));
            assertEquals(1, count(db, "StudySource"));
            sql(db, "DELETE FROM `User` WHERE username='alice'");
            assertEquals(0, count(db, "GitCredentials"));
        } finally {db.shutdown();}
    }

    private void credential(Database db, String source, String user) throws SQLException {
        sql(db, "INSERT INTO GitCredentials (study_source_id,user_username,username,encryptedSecret,origin,createdAt) VALUES ("
                + source + "," + user + ",'git','encrypted','https://example.org',CURRENT_TIMESTAMP)");
    }

    private long count(Database db, String table) throws SQLException {
        try (var c = db.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void sql(Database db, String statement) throws SQLException {
        try (var c = db.getConnection(); var s = c.createStatement()) {s.execute(statement);}
    }
}
