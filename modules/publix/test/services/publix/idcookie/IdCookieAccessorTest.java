package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import general.common.Common;
import general.common.RequestScope;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import testutils.publix.ContextMocker;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

public class IdCookieAccessorTest {

    private IdCookieSerialiser serialiser;
    private IdCookieAccessor accessor;

    private static MockedStatic<Common> commonStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(20);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }


    @Before
    public void setup() {
        serialiser = new IdCookieSerialiser();
        accessor = new IdCookieAccessor(serialiser);
    }

    @Test
    public void extract_collects_only_valid_and_stores_in_requestscope() throws Exception {
        Http.Cookie valid = buildCookie(11L);
        // not an ID cookie (wrong prefix) -> ignored
        Http.Cookie other = Http.Cookie.builder("SOME_COOKIE", "x=y").build();
        // malformed ID cookie (non-numeric index in name) -> discarded
        Http.Cookie malformed = Http.Cookie.builder(IdCookieModel.ID_COOKIE_NAME + "_X", "a=b").build();

        ContextMocker.mock(Arrays.asList(valid, other, malformed));

        IdCookieCollection col = accessor.extract();
        assertEquals(1, col.getAll().size());
        assertNotNull(col.findWithStudyResultId(11L));
        assertTrue(RequestScope.has(IdCookieCollection.class.getSimpleName()));
    }

    @Test
    public void discard_removes_cookie_and_updates_RequestScope() throws Exception {
        Http.Cookie c = buildCookie(42L);
        ContextMocker.mock(Collections.singletonList(c));
        // pre-fill
        accessor.extract();
        // now discard
        accessor.discard(42L);
        IdCookieCollection after = (IdCookieCollection) RequestScope.get(IdCookieCollection.class.getSimpleName());
        assertNull(after.findWithStudyResultId(42L));
    }

    @Test
    public void write_puts_cookie_and_updates_RequestScope() throws Exception {
        ContextMocker.mock(); // no cookies initially
        // extract empty collection
        accessor.extract();
        IdCookieModel m = buildModel(7L);
        accessor.write(m);
        IdCookieCollection after = (IdCookieCollection) RequestScope.get(IdCookieCollection.class.getSimpleName());
        assertNotNull(after.findWithStudyResultId(7L));
    }

    private Http.Cookie buildCookie(long studyResultId) {
        IdCookieModel m = buildModel(studyResultId);
        String val = serialiser.asCookieValueString(m);
        String name = IdCookieModel.ID_COOKIE_NAME + "_" + m.getIndex();
        return Http.Cookie.builder(name, val).build();
    }

    private IdCookieModel buildModel(long studyResultId) {
        IdCookieModel m = new IdCookieModel();
        m.setName(IdCookieModel.ID_COOKIE_NAME + "_" + studyResultId);
        m.setIndex((int) studyResultId);
        m.setWorkerId(100L);
        m.setWorkerType("GeneralSingle");
        m.setBatchId(200L);
        m.setStudyId(300L);
        m.setStudyResultId(studyResultId);
        m.setStudyResultUuid("uuid-" + studyResultId);
        m.setComponentId(null);
        m.setComponentResultId(null);
        m.setComponentPosition(null);
        m.setStudyAssets("dir");
        m.setUrlBasePath("/jatos");
        m.setJatosRun(JatosRun.RUN_STUDY);
        m.setCreationTime(999L);
        return m;
    }
}
