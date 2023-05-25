package migrations.common;

import general.common.Common;
import play.Logger;
import play.db.Database;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Similar to {@link play.api.db.evolutions.ApplicationEvolutions} this class handles locking of a MySQL database to
 * prevent parallel access from multiple nodes. Only works with a MySQL database - not H2!
 */
public class JatosMigrations {

    private static final Logger.ALogger LOGGER = Logger.of(JatosMigrations.class);

    private final Database db;

    @Inject
    JatosMigrations(Database db) {
        this.db = db;
    }

    public void start(Runnable callback) throws SQLException {
        if (Common.isMultiNode()) runWithLocks(callback);
        else callback.run();
    }

    private void runWithLocks(Runnable callback) throws SQLException {
        Connection c = db.getDataSource().getConnection();
        c.setAutoCommit(false);
        Statement s = c.createStatement();
        createLockTableIfNecessary(c, s);
        lock(c, s, 5);

        callback.run();

        s.close();
        c.commit();
        c.close();
    }

    private void createLockTableIfNecessary(Connection c, Statement s) throws SQLException {
        try {
            ResultSet r = s.executeQuery("select `lock` from play_evolutions_lock");
            r.close();
        } catch (SQLException e) {
            c.rollback();
            s.execute("create table play_evolutions_lock (`lock` int not null primary key)");
            s.executeUpdate("insert into play_evolutions_lock (`lock`) values (1)");
        }
    }

    private void lock(Connection c, Statement s, int attempts) throws SQLException {
        try {
            s.execute("set innodb_lock_wait_timeout = 1");
            s.execute("select `lock` from jatos.play_evolutions_lock where `lock` = 1 for update");
        } catch (SQLException e) {
            if (attempts == 0) throw e;
            else {
                LOGGER.warn("Exception while attempting to lock evolutions (other node probably has lock), sleeping for 1 sec");
                c.rollback();
                sleepASec();
                lock(c, s, attempts - 1);
            }
        }
    }

    private static void sleepASec() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
