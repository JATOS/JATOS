package migrations.common;

import org.junit.Test;
import play.db.Database;
import play.db.Databases;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.Assert.*;

public class StudyOwnerMigrationTest {
    @Test
    public void backfillsOwnersAndMembershipAndEnforcesConstraints() throws Exception {
        Database db = database();
        try {
            sql(db, "INSERT INTO Study VALUES (1),(2),(3)");
            sql(db, "INSERT INTO StudyUserMap VALUES (1,'zoe'),(1,'alice'),(3,'zoe')");
            migrate(db);
            assertEquals("alice", owner(db, 1));
            assertEquals("admin", owner(db, 2));
            assertEquals("zoe", owner(db, 3));
            try (var c = db.getConnection(); var s = c.createStatement();
                 var rs = s.executeQuery("SELECT user_username FROM StudyUserMap WHERE study_id=2")) {
                assertTrue(rs.next());
                assertEquals("admin", rs.getString(1));
            }
            assertThrows(SQLException.class, () -> sql(db, "INSERT INTO Study VALUES (4,NULL)"));
            assertThrows(SQLException.class, () -> sql(db, "INSERT INTO Study VALUES (4,'missing')"));
            assertThrows(SQLException.class, () -> sql(db, "DELETE FROM `User` WHERE username='zoe'"));
        } finally { db.shutdown(); }
    }

    @Test
    public void missingAdminRejectsOrphanRepair() throws Exception {
        Database db = database();
        try {
            sql(db, "DELETE FROM `User` WHERE username='admin'");
            sql(db, "INSERT INTO Study VALUES (1),(2)");
            sql(db, "INSERT INTO StudyUserMap VALUES (1,'alice')");
            // DDL is not transactionally rolled back on MySQL: an unsuccessful evolution
            // needs normal Play evolution repair rather than rerunning a startup callback.
            assertThrows(SQLException.class, () -> migrate(db));
            assertNull(owner(db, 2));
        } finally { db.shutdown(); }
    }

    @Test
    public void emptyDatabaseStillEnforcesOwnerConstraints() throws Exception {
        Database db = database();
        try {
            migrate(db);
            assertThrows(SQLException.class, () -> sql(db, "INSERT INTO Study (id) VALUES (1)"));
            sql(db, "INSERT INTO Study VALUES (1,'alice')");
            assertEquals("alice", owner(db, 1));
        } finally { db.shutdown(); }
    }

    private Database database() throws Exception {
        Database db = Databases.createFrom("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        sql(db, "CREATE TABLE `User` (username varchar(255) PRIMARY KEY)");
        sql(db, "CREATE TABLE Study (id bigint PRIMARY KEY)");
        sql(db, "CREATE TABLE StudyUserMap (study_id bigint REFERENCES Study(id), user_username varchar(255) REFERENCES `User`(username), PRIMARY KEY(study_id,user_username))");
        sql(db, "INSERT INTO `User` VALUES ('admin'),('alice'),('zoe')");
        return db;
    }

    private void migrate(Database db) throws Exception {
        String evolution;
        try (var stream = getClass().getResourceAsStream("/evolutions/default/26.sql")) {
            assertNotNull(stream);
            evolution = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String up = evolution.split("# --- !Ups")[1].split("# --- !Downs")[0];
        up = up.replaceAll("(?m)^#.*$", "");
        for (String statement : up.split(";")) if (!statement.isBlank()) sql(db, statement);
    }

    private String owner(Database db, long id) throws Exception {
        try (var c = db.getConnection(); var s = c.createStatement();
             var rs = s.executeQuery("SELECT owner_username FROM Study WHERE id=" + id)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private void sql(Database db, String sql) throws SQLException {
        try (var c = db.getConnection(); var s = c.createStatement()) { s.execute(sql); }
    }
}
