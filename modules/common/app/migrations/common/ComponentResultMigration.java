package migrations.common;

import com.diffplug.common.base.Errors;
import daos.common.ComponentResultDao;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import java.util.List;

/**
 * Migrates the database for all <3.7.5. It adds dataSize and the dataShort fields to each ComponentResult row.
 */
@SuppressWarnings("deprecation")
public class ComponentResultMigration {

    private static final Logger.ALogger LOGGER = Logger.of(ComponentResultMigration.class);

    private final ComponentResultDao componentResultDao;
    private final JPAApi jpaApi;
    private final JatosMigrations jatosMigrations;

    @Inject
    ComponentResultMigration(ComponentResultDao componentResultDao, JPAApi jpaApi, JatosMigrations jatosMigrations) {
        this.componentResultDao = componentResultDao;
        this.jpaApi = jpaApi;
        this.jatosMigrations = jatosMigrations;
    }

    public void run() {
        try {
            jatosMigrations.start(this::fill);
        } catch (Exception e) {
            throw new RuntimeException("ComponentResult Migration failed", e);
        }
    }

    private void fill() {
        List<Long> crids = jpaApi.withTransaction(componentResultDao::findAllIdsWhereDataSizeIsNull);
        if (crids.isEmpty()) return;

        LOGGER.info("Start filling dataSize and dataShort fields of ComponentResults. This is part of the update " +
                "and can take a while depending on the number of ComponentResults in your database.");
        crids.parallelStream().forEach(crid -> jpaApi.withTransaction(() -> {
            Errors.rethrow().run(() -> componentResultDao.setDataSizeAndDataShort(crid));
            LOGGER.info("Filled dataSize and dataShort fields of ComponentResult " + crid);
        }));
        LOGGER.info("Filled dataSize and dataShort fields in " + crids.size() + " ComponentResult entities");
    }

}
