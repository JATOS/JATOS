package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import filters.publix.IdCookieFilter;
import general.common.Common;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.WorkerType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import services.publix.idcookie.exceptions.IdCookieNotFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IdCookieServiceTest {

    private IdCookieSerialiser idCookieSerialiser;
    private IdCookieService service;

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void initStatics() {
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(20);
        commonStatic.when(Common::getJatosUrlBasePath).thenReturn("/");
        commonStatic.when(Common::isIdCookiesSecure).thenReturn(false);
        commonStatic.when(Common::getIdCookiesSameSite).thenReturn(null);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Before
    public void setup() {
        idCookieSerialiser = new IdCookieSerialiser();
        service = new IdCookieService(idCookieSerialiser);
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void hasIdCookie_returnsTrueIfCookieForStudyResultExists() {
        IdCookieModel idCookie = idCookieModel(5L);
        setCurrentContextWith(idCookie);

        assertThat(service.hasIdCookie(5L)).isTrue();
        assertThat(service.hasIdCookie(99L)).isFalse();
    }

    @Test
    public void getIdCookie_returnsCookieForStudyResult() {
        IdCookieModel idCookie = idCookieModel(5L);
        setCurrentContextWith(idCookie);

        assertThat(service.getIdCookie(5L)).isSameAs(idCookie);
    }

    @Test
    public void getIdCookie_throwsIfCookieForStudyResultDoesNotExist() {
        setCurrentContextWith(new IdCookieCollection());

        assertThatThrownBy(() -> service.getIdCookie(99L))
                .isInstanceOf(IdCookieNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    public void getJatosRun_returnsRunStoredInCookie() {
        IdCookieModel idCookie = idCookieModel(5L);
        idCookie.setJatosRun(JatosRun.RUN_STUDY);
        setCurrentContextWith(idCookie);

        assertThat(service.getJatosRun(5L)).isEqualTo(JatosRun.RUN_STUDY);
    }

    @Test
    public void oneIdCookieHasThisStudyAssets_checksAllCookiesAttachedToRequest() {
        IdCookieModel first = idCookieModel(1L);
        first.setStudyAssets("study-assets-a");

        IdCookieModel second = idCookieModel(2L);
        second.setStudyAssets("study-assets-b");

        Http.Request requestWithContext = requestWithContext(first, second);

        assertThat(service.oneIdCookieHasThisStudyAssets(requestWithContext, "study-assets-a")).isTrue();
        assertThat(service.oneIdCookieHasThisStudyAssets(requestWithContext, "study-assets-b")).isTrue();
        assertThat(service.oneIdCookieHasThisStudyAssets(requestWithContext, "unknown-assets")).isFalse();
    }

    @Test
    public void writeIdCookie_addsGeneratedCookieToCurrentCollection() {
        IdCookieCollection collection = new IdCookieCollection();
        setCurrentContextWith(collection);

        Study study = new Study();
        study.setId(1L);
        study.setDirName("study-dir");

        Batch batch = new Batch();
        batch.setId(2L);

        Component component = new Component();
        component.setId(3L);
        study.addComponent(component);

        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(4L);
        componentResult.setComponent(component);

        GeneralSingleWorker worker = new GeneralSingleWorker();
        worker.setId(9L);

        StudyResult studyResult = new StudyResult();
        studyResult.setId(7L);
        studyResult.setUuid("uuid-7");
        studyResult.setStudy(study);
        studyResult.setBatch(batch);
        studyResult.setWorker(worker);

        service.writeIdCookie(studyResult, componentResult, JatosRun.RUN_STUDY);

        IdCookieModel written = collection.findWithStudyResultId(7L);
        assertThat(written).isNotNull();
        assertThat(written.getStudyId()).isEqualTo(1L);
        assertThat(written.getBatchId()).isEqualTo(2L);
        assertThat(written.getComponentId()).isEqualTo(3L);
        assertThat(written.getComponentResultId()).isEqualTo(4L);
        assertThat(written.getWorkerId()).isEqualTo(9L);
        assertThat(written.getWorkerType()).isEqualTo(WorkerType.GENERAL_SINGLE);
        assertThat(written.getStudyAssets()).isEqualTo("study-dir");
        assertThat(written.getStudyResultId()).isEqualTo(7L);
        assertThat(written.getStudyResultUuid()).isEqualTo("uuid-7");
        assertThat(written.getJatosRun()).isEqualTo(JatosRun.RUN_STUDY);
    }

    @Test
    public void discardIdCookie_removesCookieForStudyResult() {
        IdCookieCollection collection = new IdCookieCollection();
        collection.add(idCookieModel(1L));
        collection.add(idCookieModel(2L));
        setCurrentContextWith(collection);

        service.discardIdCookie(1L);

        assertThat(collection.findWithStudyResultId(1L)).isNull();
        assertThat(collection.findWithStudyResultId(2L)).isNotNull();
    }

    @Test
    public void discardIdCookie_doesNothingIfCookieDoesNotExist() {
        IdCookieModel idCookie = idCookieModel(2L);
        IdCookieCollection collection = new IdCookieCollection();
        collection.add(idCookie);
        setCurrentContextWith(collection);

        service.discardIdCookie(99L);

        assertThat(collection.getAll()).containsExactly(idCookie);
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void maxIdCookiesReached_delegatesToCollectionIsFull() {
        commonStatic.when(Common::getIdCookiesLimit).thenReturn(1);

        IdCookieCollection collection = new IdCookieCollection();
        collection.put(idCookieModel(1L));
        setCurrentContextWith(collection);

        assertThat(service.maxIdCookiesReached()).isTrue();
    }

    @Test
    public void getOldestIdCookie_returnsCookieWithSmallestCreationTime() {
        IdCookieModel newer = idCookieModel(1L);
        newer.setCreationTime(200L);

        IdCookieModel older = idCookieModel(2L);
        older.setCreationTime(100L);

        setCurrentContextWith(newer, older);

        assertThat(service.getOldestIdCookie()).isSameAs(older);
    }

    @Test
    public void getOldestIdCookie_ignoresCookiesWithoutCreationTime() {
        IdCookieModel withoutCreationTime = idCookieModel(1L);
        withoutCreationTime.setCreationTime(null);

        IdCookieModel withCreationTime = idCookieModel(2L);
        withCreationTime.setCreationTime(100L);

        setCurrentContextWith(withoutCreationTime, withCreationTime);

        assertThat(service.getOldestIdCookie()).isSameAs(withCreationTime);
    }

    @Test
    public void getOldestIdCookie_returnsNullForEmptyCollection() {
        setCurrentContextWith(new IdCookieCollection());

        assertThat(service.getOldestIdCookie()).isNull();
    }

    @Test
    public void getStudyResultIdOfOldestIdCookie_returnsStudyResultIdOfOldestCookie() {
        IdCookieModel newer = idCookieModel(1L);
        newer.setCreationTime(200L);

        IdCookieModel older = idCookieModel(2L);
        older.setCreationTime(100L);

        setCurrentContextWith(newer, older);

        assertThat(service.getStudyResultIdOfOldestIdCookie()).isEqualTo(2L);
    }

    @Test
    public void extractIdCookieNames_returnsOnlyIdCookieNames_caseInsensitive() {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .cookie(Http.Cookie.builder("JATOS_ID_1", "value").build())
                .cookie(Http.Cookie.builder("jatos_id_2", "value").build())
                .cookie(Http.Cookie.builder("OTHER_COOKIE", "value").build())
                .build();

        Set<String> names = service.extractIdCookieNames(request.cookies());

        assertThat(names).containsExactlyInAnyOrder("JATOS_ID_1", "jatos_id_2");
    }

    @Test
    public void extractFromCookies_extractsValidIdCookiesAndIgnoresOtherCookies() {
        IdCookieModel model = idCookieModel(11L);
        Http.Cookie validIdCookie = Http.Cookie.builder(
                        model.getName(),
                        idCookieSerialiser.asCookieValueString(model))
                .build();

        Http.Cookie otherCookie = Http.Cookie.builder("OTHER_COOKIE", "x=y").build();

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .cookie(validIdCookie)
                .cookie(otherCookie)
                .build();

        IdCookieCollection collection = service.extractFromCookies(request.cookies());

        assertThat(collection.getAll()).hasSize(1);
        assertThat(collection.findWithStudyResultId(11L)).isNotNull();
        assertThat(collection.findWithStudyResultId(11L).getName()).isEqualTo(model.getName());
    }

    @Test
    public void extractFromCookies_ignoresMalformedIdCookies() {
        Http.Cookie malformedIdCookie = Http.Cookie.builder(IdCookieModel.ID_COOKIE_NAME + "_notANumber", "x=y").build();

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .cookie(malformedIdCookie)
                .build();

        IdCookieCollection collection = service.extractFromCookies(request.cookies());

        assertThat(collection.getAll()).isEmpty();
    }

    @Test
    public void extractFromCookies_decodesUrlEncodedStringValues() {
        IdCookieModel model = idCookieModel(11L);
        model.setStudyAssets("study assets with spaces");
        model.setUrlBasePath("/my jatos/");
        model.setStudyResultUuid("uuid with spaces");

        Http.Cookie cookie = Http.Cookie.builder(
                        model.getName(),
                        idCookieSerialiser.asCookieValueString(model))
                .build();

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .cookie(cookie)
                .build();

        IdCookieCollection collection = service.extractFromCookies(request.cookies());
        IdCookieModel extracted = collection.findWithStudyResultId(11L);

        assertThat(extracted).isNotNull();
        assertThat(extracted.getStudyAssets()).isEqualTo("study assets with spaces");
        assertThat(extracted.getUrlBasePath()).isEqualTo("/my jatos/");
        assertThat(extracted.getStudyResultUuid()).isEqualTo("uuid with spaces");
    }

    @Test
    public void generatePlayCookie_serialisesCurrentIdCookie() {
        IdCookieSerialiser serialiser = mock(IdCookieSerialiser.class);
        IdCookieService serviceWithMockedSerialiser = new IdCookieService(serialiser);

        IdCookieModel idCookie = idCookieModel(5L);
        setCurrentContextWith(idCookie);

        when(serialiser.asCookieValueString(idCookie)).thenReturn("serialised=value");

        Http.Cookie cookies = serviceWithMockedSerialiser.generatePlayCookie(idCookie);

        assertThat(cookies.name()).isEqualTo(idCookie.getName());
        assertThat(cookies.value()).isEqualTo("serialised=value");
        assertThat(cookies.httpOnly()).isFalse();
        assertThat(cookies.path()).contains("/");
        assertThat(cookies.secure()).isFalse();
    }

    @Test
    public void generateDiscardCookies_generatesDiscardCookiesForGivenNames() {
        Http.Cookie[] discardCookies = service.generateDiscardCookies(Set.of("JATOS_ID_1", "JATOS_ID_2"));

        assertThat(discardCookies).hasSize(2);
        assertThat(discardCookies)
                .extracting(Http.Cookie::name)
                .containsExactlyInAnyOrder("JATOS_ID_1", "JATOS_ID_2");
        assertThat(discardCookies)
                .allSatisfy(cookie -> {
                    assertThat(cookie.maxAge()).isEqualTo(0);
                    assertThat(cookie.path()).contains("/");
                    assertThat(cookie.secure()).isFalse();
                });
    }

    private void setCurrentContextWith(IdCookieModel... idCookies) {
        IdCookieCollection collection = new IdCookieCollection();
        for (IdCookieModel idCookie : idCookies) {
            collection.add(idCookie);
        }
        setCurrentContextWith(collection);
    }

    private void setCurrentContextWith(IdCookieCollection collection) {
        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .build();

        Context context = new Context(request);
        context.args().put(IdCookieFilter.IDCOOKIES_TYPED_KEY, collection);
        Context.setCurrent(context);
    }

    private Http.Request requestWithContext(IdCookieModel... idCookies) {
        IdCookieCollection collection = new IdCookieCollection();
        for (IdCookieModel idCookie : idCookies) {
            collection.add(idCookie);
        }

        Http.Request request = new Http.RequestBuilder()
                .method("GET")
                .uri("/")
                .build();

        Context context = new Context(request);
        context.args().put(IdCookieFilter.IDCOOKIES_TYPED_KEY, collection);

        Http.Request requestWithContext = request.addAttr(Context.CONTEXT_TYPED_KEY, context);
        return requestWithContext;
    }

    private IdCookieModel idCookieModel(long studyResultId) {
        IdCookieModel model = new IdCookieModel();
        model.setName(IdCookieModel.ID_COOKIE_NAME + "_" + studyResultId);
        model.setIndex((int) studyResultId);
        model.setWorkerId(100L);
        model.setWorkerType(WorkerType.GENERAL_SINGLE);
        model.setBatchId(200L);
        model.setStudyId(300L);
        model.setStudyResultId(studyResultId);
        model.setStudyResultUuid("uuid-" + studyResultId);
        model.setComponentId(null);
        model.setComponentResultId(null);
        model.setComponentPosition(null);
        model.setStudyAssets("study-assets-" + studyResultId);
        model.setUrlBasePath("/");
        model.setJatosRun(JatosRun.RUN_STUDY);
        model.setCreationTime(999L);
        return model;
    }

}