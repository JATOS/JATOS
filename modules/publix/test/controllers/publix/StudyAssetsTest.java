package controllers.publix;

import daos.common.StudyResultDao;
import models.common.Study;
import models.common.StudyResult;
import org.junit.Before;
import org.junit.Test;
import play.api.mvc.Action;
import play.api.mvc.ControllerComponents;
import play.db.jpa.JPAApi;
import services.publix.idcookie.IdCookieService;
import utils.common.IOUtils;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudyAssets class.
 */
public class StudyAssetsTest {

    private StudyResultDao studyResultDao;

    private StudyAssets studyAssets;

    @Before
    public void setUp() {
        // We don't need IOUtils for the tested methods here; pass null to avoid heavy initialization
        IOUtils ioUtils = null;
        IdCookieService idCookieService = mock(IdCookieService.class);
        JPAApi jpa = mock(JPAApi.class);
        // make JPAApi.withTransaction just execute the supplier
        when(jpa.withTransaction(any(Supplier.class))).thenAnswer(inv -> {
            Supplier<?> supplier = (Supplier<?>) inv.getArgument(0);
            return supplier.get();
        });
        studyResultDao = mock(StudyResultDao.class);
        Assets assets = mock(Assets.class);
        ControllerComponents controllerComponents = mock(ControllerComponents.class);

        studyAssets = new StudyAssets(controllerComponents, ioUtils, idCookieService, jpa, studyResultDao, assets);
    }

    @Test
    public void enhanceQueryStringInEndRedirectUrl_replacesAndEncodes() {
        String urlQueryParameters = "{\"batchId\":\"1\",\"SONA_ID\":\"123 abc\"}";
        String endRedirectUrl = "https://example.org/end?foo=100&survey_id=[SONA_ID]&bar=[unknown]";

        String result = studyAssets.enhanceQueryStringInEndRedirectUrl(urlQueryParameters, endRedirectUrl);

        // SONA_ID should be URL-encoded (space -> '+'), unknown becomes 'undefined'
        assertEquals("https://example.org/end?foo=100&survey_id=123+abc&bar=undefined", result);
    }

    @Test
    public void viaStudyPath_withJatosJs_doesNotHitDbOrDelegate() {
        // Spy to check that delegation doesn't happen
        StudyAssets spy = spy(studyAssets);

        spy.viaStudyPath("anyUuid", "ignored", "jatos.js");

        verify(studyResultDao, never()).findByUuid(any());
        verify(spy, never()).viaAssetsPath(any());
    }

    @Test
    public void viaStudyPath_withJatosMinJs_doesNotHitDbOrDelegate() {
        StudyAssets spy = spy(studyAssets);

        spy.viaStudyPath("anyUuid", "ignored", "jatos.min.js");

        verify(studyResultDao, never()).findByUuid(any());
        verify(spy, never()).viaAssetsPath(any());
    }

    @Test
    public void viaStudyPath_withJatosPublixPath_doesNotHitDbOrDelegate() {
        StudyAssets spy = spy(studyAssets);

        spy.viaStudyPath("anyUuid", "ignored", "jatos-publix/dist/some/file.js");

        verify(studyResultDao, never()).findByUuid(any());
        verify(spy, never()).viaAssetsPath(any());
    }

    @Test
    public void viaStudyPath_dbPathDelegatesToViaAssetsPathWithStudyDir() {
        // Spy so we can verify delegation into viaAssetsPath
        StudyAssets spy = spy(studyAssets);
        Action<?> delegated = mock(Action.class);
        doReturn(delegated).when(spy).viaAssetsPath(any());

        // Mock DB result
        Study study = new Study();
        study.setDirName("studyDir");
        StudyResult sr = new StudyResult();
        sr.setStudy(study);
        when(studyResultDao.findByUuid("uuid-1")).thenReturn(Optional.of(sr));

        Object result = spy.viaStudyPath("uuid-1", "ignored", "foo/bar.txt");

        verify(studyResultDao).findByUuid("uuid-1");
        // Should prepend the study's dir name and a '/'
        verify(spy).viaAssetsPath("studyDir/" + "foo/bar.txt");
        assertNotNull(result);
    }

}
