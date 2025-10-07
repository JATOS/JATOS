package services.publix.workers;

import controllers.publix.Publix;
import general.common.Common;
import models.common.Study;
import models.common.workers.PersonalSingleWorker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import testutils.common.ContextMocker;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;
import static play.mvc.Http.Cookie.SameSite;
import static play.mvc.Http.Cookie.builder;

/**
 * Unit tests for GeneralSingleCookieService.
 */
public class GeneralSingleCookieServiceTest {

    private static MockedStatic<Common> commonStatic;

    private GeneralSingleCookieService service;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getJatosUrlBasePath).thenReturn("/jatos");
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Before
    public void setup() {
        // Fresh Play context without cookies
        ContextMocker.mock();
        service = new GeneralSingleCookieService();
    }

    @Test
    public void fetchWorkerIdByStudy_returnsNullWhenNoCookie() {
        Study study = new Study();
        study.setUuid("s-1");

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertNull(workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_returnsWorkerIdWhenSingleMatch() {
        Study study = new Study();
        study.setUuid("s-2");
        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, "s-2=5").build();
        ContextMocker.mock(c);

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(5L), workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_returnsLatestWorkerIdWhenMultipleForSameStudy() {
        Study study = new Study();
        study.setUuid("s-3");
        String val = "s-3=2&s-3=5&s-9=3"; // should pick 5 (latest/highest workerId)
        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, val).build();
        ContextMocker.mock(c);

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(5L), workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_ignoresMalformedEntries() {
        Study study = new Study();
        study.setUuid("s-4");
        String val = "bad&s-4=NaN&=3&s-4=7&something=else"; // only valid is s-4=7
        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, val).build();
        ContextMocker.mock(c);

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(7L), workerId);
    }

    @Test
    public void addStudy_createsNewWhenNoExistingCookie() {
        Study study = new Study();
        study.setUuid("s-5");
        PersonalSingleWorker w = new PersonalSingleWorker();
        w.setId(9L);

        String newVal = service.addStudy(study, w, null);
        assertEquals("s-5=9", newVal);
    }

    @Test
    public void addStudy_appendsWhenExistingCookie() {
        Study study = new Study();
        study.setUuid("s-6");
        PersonalSingleWorker w = new PersonalSingleWorker();
        w.setId(10L);
        Http.Cookie existing = builder("any", "a=1").build();

        String newVal = service.addStudy(study, w, existing);
        assertEquals("a=1&s-6=10", newVal);
    }

    @Test
    public void set_String_buildsCookieWithExpectedAttributes() {
        String value = "abc=123";
        service.set(value);

        Optional<Http.Cookie> opt = Publix.response().cookie(GeneralSingleCookieService.COOKIE_NAME);
        assertTrue(opt.isPresent());
        Http.Cookie c = opt.get();
        assertEquals(GeneralSingleCookieService.COOKIE_NAME, c.name());
        assertEquals(value, c.value());
        assertEquals("/jatos", c.path());
        assertTrue(c.httpOnly());
        assertFalse(c.secure());
        assertEquals(SameSite.LAX, c.sameSite().orElse(null));
        // Max age should be 10000 days (in seconds)
        Integer expectedSeconds = (int) Duration.of(10000, ChronoUnit.DAYS).getSeconds();
        assertEquals(expectedSeconds, c.maxAge());
    }

}
