package services.publix.idcookie;

import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import general.common.RequestScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.Cookie;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests class IdCookieAccessor
 *
 * @author Kristian Lange
 */
public class IdCookieAccessorTest {

    @Inject
    private TestHelper testHelper;

    @Inject
    private IdCookieAccessor idCookieAccessor;

    @Inject
    private IdCookieTestHelper idCookieTestHelper;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Injector injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @Test
    public void checkExtractSize() throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        IdCookieModel idCookie2 = idCookieTestHelper.buildDummyIdCookie(2l);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));
        cookieList.add(idCookieTestHelper.buildCookie(idCookie2));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(2);
        assertThat(idCookieCollection.findWithStudyResultId(1l))
                .isEqualTo(idCookie1);
        assertThat(idCookieCollection.findWithStudyResultId(2l))
                .isEqualTo(idCookie2);
    }

    @Test
    public void checkExtractEmpty() throws IdCookieAlreadyExistsException {
        List<Cookie> cookieList = new ArrayList<>();

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(0);
    }

    @Test
    public void checkExtractCheckValues()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(1);
        IdCookieModel idCookie1Extracted = idCookieCollection
                .findWithStudyResultId(1l);
        idCookieTestHelper.checkDummyIdCookie(idCookie1Extracted);
        assertThat(RequestScope.has(IdCookieCollection.class.getSimpleName()))
                .isTrue();
    }

    @Test
    public void checkExtractMalformedIndex()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setName("JATOS_IDS"); // No index
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        // Since cookie is malformed it should be removed
        assertThat(idCookieCollection.size()).isEqualTo(0);
    }

    @Test
    public void checkExtractMalformedIdStrict()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setBatchId(null);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        // Since cookie is malformed it should be removed
        assertThat(idCookieCollection.size()).isEqualTo(0);
    }

    @Test
    public void checkExtractMalformedIdNotStrict()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setComponentId(null); // Not necessary
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        // Component ID is not necessary
        assertThat(idCookieCollection.size()).isEqualTo(1);
    }

    @Test
    public void checkExtractMalformedName()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setName("FOO_0"); // Wrong name but with index
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        // Since cookie is malformed it should be removed
        assertThat(idCookieCollection.size()).isEqualTo(0);
    }

    @Test
    public void checkExtractMalformedStudyAssets()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setStudyAssets(""); // Malformed study assets
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        // Since cookie is malformed it should be removed
        assertThat(idCookieCollection.size()).isEqualTo(0);
    }

    @Test
    public void checkExtractJatosRun() throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        idCookie1.setJatosRun(null); // JatosRun can be null
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(1);
    }

    @Test
    public void checkExtractFromRequestScope()
            throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        IdCookieCollection idCookieCollection = new IdCookieCollection();
        idCookieCollection.add(idCookie1);

        RequestScope.put(IdCookieCollection.class.getSimpleName(),
                idCookieCollection);

        // Extract from RequestScope instead of Request object
        idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(1);
        IdCookieModel idCookie1Extracted = idCookieCollection
                .findWithStudyResultId(1l);
        idCookieTestHelper.checkDummyIdCookie(idCookie1Extracted);
        assertThat(RequestScope.has(IdCookieCollection.class.getSimpleName()))
                .isTrue();
    }

    @Test
    public void checkDiscard() throws IdCookieAlreadyExistsException {
        IdCookieModel idCookie1 = idCookieTestHelper.buildDummyIdCookie(1l);
        List<Cookie> cookieList = new ArrayList<>();
        cookieList.add(idCookieTestHelper.buildCookie(idCookie1));

        testHelper.mockContext(cookieList);

        // extract() puts it in the RequestScope too
        IdCookieCollection idCookieCollection = idCookieAccessor.extract();
        assertThat(idCookieCollection.size()).isEqualTo(1);

        idCookieAccessor.discard(1l);

        // Check in RequestScope
        idCookieCollection = (IdCookieCollection) RequestScope
                .get(IdCookieCollection.class.getSimpleName());
        assertThat(idCookieCollection.size()).isEqualTo(0);

        // Check in Response object
        // TODO How to check response?
        // assertThat(Http.Context.current.get().response().cookie("JATOS_IDS_0"))
        // .isNull();
    }

}
