
package daos.common;

import models.common.Component;
import models.common.ComponentResult;
import org.junit.Test;
import testutils.JatosTest;

import javax.inject.Inject;

import static org.junit.Assert.*;

public class ComponentResultDaoTest extends JatosTest {

    @Inject
    private ComponentResultDao componentResultDao;

    @Inject
    private ComponentDao componentDao;

    @Test
    public void testNewResultsDoNotNeedDataSizeMigration() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult defaultResult = new ComponentResult();
        defaultResult.setComponent(component);
        for (ComponentResult cr : new ComponentResult[]{defaultResult, new ComponentResult(component)}) {
            componentResultDao.persist(cr);

            // Read the column directly: getDataSize() also returns zero for a database NULL.
            Number storedSize = componentResultDao.withReadOnlyTransaction(em ->
                    (Number) em.createNativeQuery("SELECT dataSize FROM ComponentResult WHERE id = :id")
                    .setParameter("id", cr.getId())
                    .getSingleResult());
            assertNotNull(storedSize);
            assertEquals(0L, storedSize.longValue());
            assertFalse(componentResultDao.findAllIdsWhereDataSizeIsNull().contains(cr.getId()));
        }
    }

    @Test
    public void testLegacyResultWithoutDataIsMigratedOnlyOnce() {
        Component component = new Component();
        componentDao.persist(component);
        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.withTransaction(em -> {
            em.createNativeQuery("UPDATE ComponentResult SET dataSize = NULL WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
        });
        assertTrue(componentResultDao.findAllIdsWhereDataSizeIsNull().contains(id));

        componentResultDao.setDataSizeAndDataShort(id);

        assertFalse(componentResultDao.findAllIdsWhereDataSizeIsNull().contains(id));
        assertEquals(Integer.valueOf(0), componentResultDao.findById(id).getDataSize());
    }

    @Test
    public void testReplaceDataAndGetData() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        String data = "Hello, World!";
        componentResultDao.replaceData(id, data);

        String fetchedData = componentResultDao.getData(id);
        assertEquals(data, fetchedData);

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals(data, updatedCr.getDataShort());
        assertEquals(Integer.valueOf(data.length()), updatedCr.getDataSize());
    }

    @Test
    public void testReplaceDataTruncatesDataShort() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        String longData = "A".repeat(1200);
        componentResultDao.replaceData(id, longData);

        assertEquals(longData, componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals(1000, updatedCr.getDataShort().length());
        assertEquals("A".repeat(1000), updatedCr.getDataShort());
        assertEquals(Integer.valueOf(1200), updatedCr.getDataSize());
    }

    @Test
    public void testAppendDataToNullData() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.appendData(id, "Some data");

        assertEquals("Some data", componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals("Some data", updatedCr.getDataShort());
        assertEquals(Integer.valueOf(9), updatedCr.getDataSize());
    }

    @Test
    public void testAppendDataTwiceShort() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.appendData(id, "Part 1: ");
        componentResultDao.appendData(id, "Part 2");

        assertEquals("Part 1: Part 2", componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals("Part 1: Part 2", updatedCr.getDataShort());
        assertEquals(Integer.valueOf(14), updatedCr.getDataSize());
    }

    @Test
    public void testAppendDataTwiceLong() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        String longDataA = "A".repeat(600);
        componentResultDao.appendData(id, longDataA);
        String longDataB = "B".repeat(600);
        componentResultDao.appendData(id, longDataB);

        assertEquals(longDataA + longDataB, componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals(1000, updatedCr.getDataShort().length());
        assertEquals("A".repeat(600) + "B".repeat(400), updatedCr.getDataShort());
        assertEquals(Integer.valueOf(1200), updatedCr.getDataSize());
    }

    @Test
    public void testAppendDataNonAscii() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.appendData(id, "你经常来吗");
        assertEquals("你经常来吗", componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals("你经常来吗", updatedCr.getDataShort());
        assertEquals(Integer.valueOf(15), updatedCr.getDataSize());
    }

    @Test
    public void testPurgeData() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.replaceData(id, "Some sensitive data");
        assertNotNull(componentResultDao.getData(id));

        componentResultDao.purgeData(id);

        assertNull(componentResultDao.getData(id));

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertNull(updatedCr.getDataShort());
        assertEquals(Integer.valueOf(0), updatedCr.getDataSize());
    }

    @Test
    public void testSetDataSizeAndDataShort() {
        Component component = new Component();
        componentDao.persist(component);

        ComponentResult cr = new ComponentResult(component);
        componentResultDao.persist(cr);
        Long id = cr.getId();

        componentResultDao.replaceData(id, "Data for size and short fields");
        componentResultDao.setDataSizeAndDataShort(id);

        ComponentResult updatedCr = componentResultDao.findById(id);
        assertEquals("Data for size and short fields", updatedCr.getDataShort());
        assertEquals(Integer.valueOf(30), updatedCr.getDataSize());
    }

}
