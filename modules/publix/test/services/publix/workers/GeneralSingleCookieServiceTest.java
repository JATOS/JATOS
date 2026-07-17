package services.publix.workers;

import general.common.Common;
import http.common.Http.Context;
import models.common.Study;
import models.common.workers.Worker;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import play.test.Helpers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
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
        service = new GeneralSingleCookieService();
    }

    @Test
    public void fetchWorkerIdByStudy_returnsNullWhenNoCookie() {
        Study study = new Study();
        study.setUuid("s-1");

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertNull(workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_returnsWorkerIdWhenSingleMatch() {
        Study study = new Study();
        study.setUuid("s-2");

        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, "s-2=5").build();
        Context.setCurrent(new Context(Helpers.fakeRequest().cookie(c).build()));

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(5L), workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_returnsLatestWorkerIdWhenMultipleForSameStudy() {
        Study study = new Study();
        study.setUuid("s-3");

        String val = "s-3=2&s-3=5&s-9=3"; // should pick 5 (latest/highest workerId)
        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, val).build();
        Context.setCurrent(new Context(Helpers.fakeRequest().cookie(c).build()));

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(5L), workerId);
    }

    @Test
    public void fetchWorkerIdByStudy_ignoresMalformedEntries() {
        Study study = new Study();
        study.setUuid("s-4");

        String val = "bad&s-4=NaN&=3&s-4=7&something=else"; // only valid is s-4=7
        Http.Cookie c = builder(GeneralSingleCookieService.COOKIE_NAME, val).build();
        Context.setCurrent(new Context(Helpers.fakeRequest().cookie(c).build()));

        Long workerId = service.fetchWorkerIdByStudy(study);
        assertEquals(Long.valueOf(7L), workerId);
    }

    @Test
    public void generate_returnsNewCookieWhenNoCurrentCookie() {
        Study study = new Study();
        study.setUuid("s-5");
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(8L);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));

        Http.Cookie cookie = service.generate(study, worker);

        assertEquals(GeneralSingleCookieService.COOKIE_NAME, cookie.name());
        assertEquals("s-5=8", cookie.value());
        assertEquals("/jatos", cookie.path());
        assertTrue(cookie.httpOnly());
        assertFalse(cookie.secure());
        assertEquals(SameSite.LAX, cookie.sameSite().orElse(null));
        Integer expectedSeconds = (int) Duration.of(10000, ChronoUnit.DAYS).getSeconds();
        assertEquals(expectedSeconds, cookie.maxAge());
    }

    @Test
    public void generate_appendsStudyAndWorkerWhenCurrentCookieDoesNotContainStudy() {
        Study study = new Study();
        study.setUuid("s-6");
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(9L);

        Http.Cookie currentCookie = builder(GeneralSingleCookieService.COOKIE_NAME, "s-1=2&s-2=3").build();
        Context.setCurrent(new Context(Helpers.fakeRequest().cookie(currentCookie).build()));

        Http.Cookie cookie = service.generate(study, worker);

        assertEquals(GeneralSingleCookieService.COOKIE_NAME, cookie.name());
        assertEquals("s-1=2&s-2=3&s-6=9", cookie.value());
        assertEquals("/jatos", cookie.path());
        assertTrue(cookie.httpOnly());
        assertFalse(cookie.secure());
        assertEquals(SameSite.LAX, cookie.sameSite().orElse(null));
        Integer expectedSeconds = (int) Duration.of(10000, ChronoUnit.DAYS).getSeconds();
        assertEquals(expectedSeconds, cookie.maxAge());
    }

    @Test
    public void generate_keepsCurrentCookieValueWhenStudyAlreadyExists() {
        Study study = new Study();
        study.setUuid("s-7");
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(10L);

        String currentValue = "s-1=2&s-7=5&s-2=3";
        Http.Cookie currentCookie = builder(GeneralSingleCookieService.COOKIE_NAME, currentValue).build();
        Context.setCurrent(new Context(Helpers.fakeRequest().cookie(currentCookie).build()));

        Http.Cookie cookie = service.generate(study, worker);

        assertEquals(GeneralSingleCookieService.COOKIE_NAME, cookie.name());
        assertEquals(currentValue, cookie.value());
        assertEquals("/jatos", cookie.path());
        assertTrue(cookie.httpOnly());
        assertFalse(cookie.secure());
        assertEquals(SameSite.LAX, cookie.sameSite().orElse(null));
        Integer expectedSeconds = (int) Duration.of(10000, ChronoUnit.DAYS).getSeconds();
        assertEquals(expectedSeconds, cookie.maxAge());
    }

}
