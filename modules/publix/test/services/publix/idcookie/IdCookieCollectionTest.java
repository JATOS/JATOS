package services.publix.idcookie;

import general.common.Common;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class IdCookieCollectionTest {

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Test
    public void add_put_remove_and_lookup_behaviour() throws Exception {
        IdCookieCollection col = new IdCookieCollection();

        IdCookieModel c1 = build(10L);
        IdCookieModel c2 = build(20L);

        // add stores new ones
        col.add(c1);
        col.add(c2);
        assertEquals(2, col.getAll().size());
        assertSame(c1, col.findWithStudyResultId(10L));
        assertSame(c2, col.findWithStudyResultId(20L));

        // put overwrites existing key
        IdCookieModel c1b = build(10L);
        c1b.setName("overwrite");
        col.put(c1b);
        assertSame(c1b, col.findWithStudyResultId(10L));

        // remove
        col.remove(c2);
        assertNull(col.findWithStudyResultId(20L));
    }

    @Test(expected = IdCookieAlreadyExistsException.class)
    public void add_throws_on_duplicate_key() throws Exception {
        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel c1 = build(1L);
        IdCookieModel c1dup = build(1L);
        col.add(c1);
        col.add(c1dup); // should throw
    }

    @Test(expected = IdCookieCollectionFullException.class)
    public void put_throws_when_full_and_new_key() throws Exception {
        // Set limit to 1
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(1);

        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel c1 = build(1L);
        IdCookieModel c2 = build(2L);
        col.put(c1); // ok, now full
        col.put(c2); // new key and full -> throw
    }

    @Test
    public void put_allows_overwrite_even_when_full() throws Exception {
        // Set limit to 1
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(1);

        IdCookieCollection col = new IdCookieCollection();
        IdCookieModel c1 = build(1L);
        col.put(c1); // ok, now full
        IdCookieModel c1b = build(1L);
        c1b.setName("overwrite");
        col.put(c1b); // overwrite existing key is allowed
        assertSame(c1b, col.findWithStudyResultId(1L));
    }

    private static IdCookieModel build(Long studyResultId) {
        IdCookieModel m = new IdCookieModel();
        m.setStudyResultId(studyResultId);
        m.setName(IdCookieModel.ID_COOKIE_NAME + "_" + studyResultId);
        return m;
    }
}
