package migrations.common;

import general.common.Common;
import org.junit.Test;
import play.db.Database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JatosMigrationsTest {
    @Test public void migrationUsesCurrentDatabaseAndReleasesLockOnSuccess() throws Exception {
        checkLockCleanup(false);
    }

    @Test public void failedMigrationRollsBackAndReleasesLock() throws Exception {
        checkLockCleanup(true);
    }

    private void checkLockCleanup(boolean fail) throws Exception {
        Database db = mock(Database.class);
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(db.getDataSource()).thenReturn(source);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select `lock` from play_evolutions_lock")).thenReturn(mock(ResultSet.class));
        try (var common = mockStatic(Common.class)) {
            common.when(Common::isMultiNode).thenReturn(true);
            JatosMigrations migrations = new JatosMigrations(db);
            RuntimeException failure = new RuntimeException("migration failed");
            if (fail) {
                assertSame(failure, assertThrows(RuntimeException.class, () -> migrations.start(() -> { throw failure; })));
                verify(connection).rollback();
                verify(connection, never()).commit();
            } else {
                migrations.start(() -> {});
                verify(connection).commit();
            }
            verify(statement).execute("select `lock` from play_evolutions_lock where `lock` = 1 for update");
            verify(statement).close();
            verify(connection).close();
        }
    }
}
