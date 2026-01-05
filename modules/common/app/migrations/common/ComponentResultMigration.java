package migrations.common;

import daos.common.ComponentResultDao;
import play.Logger;

import javax.inject.Inject;
import java.util.List;

/**
 * Migrates the database for all <3.7.5. It adds dataSize and the dataShort fields to each ComponentResult row.
 */
public class ComponentResultMigration {

    private static final Logger.ALogger LOGGER = Logger.of(ComponentResultMigration.class);

    private final ComponentResultDao componentResultDao;
    private final JatosMigrations jatosMigrations;

    @Inject
    ComponentResultMigration(ComponentResultDao componentResultDao, JatosMigrations jatosMigrations) {
        this.componentResultDao = componentResultDao;
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
        List<Long> crids = componentResultDao.findAllIdsWhereDataSizeIsNull();
        if (crids.isEmpty()) return;

        LOGGER.info("Start filling dataSize and dataShort fields of ComponentResults. This is part of the update " +
                "and can take a while depending on the number of ComponentResults in your database.");
        crids.parallelStream().forEach(crid -> {
            componentResultDao.setDataSizeAndDataShort(crid);
            LOGGER.info("Filled dataSize and dataShort fields of ComponentResult " + crid);
        });
        LOGGER.info("Filled dataSize and dataShort fields in " + crids.size() + " ComponentResult entities");
    }

}
