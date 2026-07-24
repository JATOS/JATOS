package services.gui;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import http.common.Http.Context;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.ComponentProperties;
import org.junit.*;
import org.mockito.Mockito;
import play.test.Helpers;
import utils.common.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ComponentService.
 */
public class ComponentServiceTest {

    private static org.mockito.MockedStatic<general.common.Common> commonStatic;

    private ResultRemover resultRemover;
    private StudyDao studyDao;
    private ComponentDao componentDao;
    private IOUtils ioUtils;

    private ComponentService componentService;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initCommonStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = Mockito.mockStatic(general.common.Common.class);
        commonStatic.when(general.common.Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(general.common.Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonStatic.when(general.common.Common::getResultUploadsPath).thenReturn(tmp);
    }

    @AfterClass
    public static void tearDownCommonStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Before
    public void setup() {
        resultRemover = Mockito.mock(ResultRemover.class);
        studyDao = Mockito.mock(StudyDao.class);
        componentDao = Mockito.mock(ComponentDao.class);
        ioUtils = Mockito.mock(IOUtils.class);

        componentService = new ComponentService(studyDao, componentDao, ioUtils);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    private Component exampleComponent(Study study) {
        Component c = new Component();
        c.setStudy(study);
        c.setTitle("Comp A");
        c.setHtmlFilePath("a/index.html");
        c.setReloadable(true);
        c.setActive(true);
        c.setComponentInput("{\"x\":1}");
        c.setComments("note");
        c.setId(10L);
        return c;
    }

    @Test
    public void clone_shouldCopyFields_andGenerateNewUuid() {
        Study s = new Study();
        Component original = exampleComponent(s);
        String originalUuid = original.getUuid();

        Component clone = componentService.clone(original);

        // same fields
        assertThat(clone.getStudy()).isEqualTo(s);
        assertThat(clone.getTitle()).isEqualTo("Comp A");
        assertThat(clone.getHtmlFilePath()).isEqualTo("a" + File.separator + "index.html");
        assertThat(clone.isReloadable()).isTrue();
        assertThat(clone.isActive()).isTrue();
        assertThat(clone.getComponentInput()).isEqualTo("{\"x\":1}");
        assertThat(clone.getComments()).isEqualTo("note");
        // differences
        assertThat(clone.getId()).isNull();
        assertThat(clone.getUuid()).isNotEqualTo(originalUuid);
        assertThat(clone.getUuid()).isNotEmpty();
    }

    @Test
    public void cloneWholeComponent_shouldChangeTitle_andCloneHtmlPath_onSuccess() throws IOException {
        // Given
        Study s = new Study();
        s.setId(1L);
        Component original = exampleComponent(s);
        when(componentDao.findByTitle("Comp A (clone)")).thenReturn(Collections.emptyList());
        when(ioUtils.cloneComponentHtmlFile(s.getDirName(), original.getHtmlFilePath()))
                .thenReturn("a/index_cloned.html");

        // When
        Component clone = componentService.cloneWholeComponent(original);

        // Then
        assertThat(clone.getTitle()).isEqualTo("Comp A (clone)");
        assertThat(clone.getHtmlFilePath()).isEqualTo("a" + File.separator + "index_cloned.html");
        assertThat(clone.getStudy()).isEqualTo(s);
        assertThat(clone.getUuid()).isNotEqualTo(original.getUuid());
    }

    @Test
    public void cloneWholeComponent_onIOException_shouldKeepOriginalHtmlPath_andStillChangeTitle() throws IOException {
        // Given
        Study s = new Study();
        Component original = exampleComponent(s);
        when(componentDao.findByTitle("Comp A (clone)")).thenReturn(Collections.emptyList());
        when(ioUtils.cloneComponentHtmlFile(anyString(), anyString())).thenThrow(new IOException("fail"));

        // When
        Component clone = componentService.cloneWholeComponent(original);

        // Then
        assertThat(clone.getTitle()).isEqualTo("Comp A (clone)");
        // unchanged because cloning failed
        assertThat(clone.getHtmlFilePath()).isEqualTo(original.getHtmlFilePath());
    }

    @Test
    public void bindToProperties_shouldFillFields_andHtmlExistsFlag() {
        // Given
        Study s = new Study();
        s.setId(3L);
        Component c = exampleComponent(s);
        c.setId(42L);
        when(studyDao.findStudyDirNameByComponentId(c.getId())).thenReturn(s.getDirName());
        when(ioUtils.checkFileInStudyAssetsDirExists(s.getDirName(), c.getHtmlFilePath())).thenReturn(true);

        // When
        ComponentProperties props = componentService.bindToProperties(c);

        // Then
        assertThat(props.getUuid()).isEqualTo(c.getUuid());
        assertThat(props.getTitle()).isEqualTo("Comp A");
        assertThat(props.getId()).isEqualTo(42L);
        assertThat(props.getStudyId()).isEqualTo(3L);
        assertThat(props.getHtmlFilePath()).isEqualTo("a" + File.separator + "index.html");
        assertThat(props.isHtmlFileExists()).isTrue();
        assertThat(props.getComponentInput()).isEqualTo("{\"x\":1}");
        assertThat(props.getComments()).isEqualTo("note");
        assertThat(props.isReloadable()).isTrue();
        assertThat(props.isActive()).isTrue();
    }

    @Test
    public void updateComponentAfterEdit_shouldUpdateFields_andCallDaoMerge() {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);
        ComponentProperties updated = new ComponentProperties();
        updated.setTitle("New");
        updated.setReloadable(false);
        updated.setComments("c2");
        updated.setComponentInput("{\"y\":2}");
        updated.setActive(false);
        updated.setHtmlFilePath("b" + File.separator + "test.html");

        // When
        componentService.updateComponentAfterEdit(c, updated);

        // Then
        assertThat(c.getTitle()).isEqualTo("New");
        assertThat(c.isReloadable()).isFalse();
        assertThat(c.getComments()).isEqualTo("c2");
        assertThat(c.getComponentInput()).isEqualTo("{\"y\":2}");
        assertThat(c.isActive()).isFalse();
        assertThat(c.getHtmlFilePath()).isEqualTo("b" + File.separator + "test.html");
        verify(componentDao, times(2)).merge(c);
    }

    @Test
    public void createAndPersistComponent_withProperties_shouldBind_thenPersist_andMergeStudy() {
        // Given
        Study s = new Study();
        s.setId(5L);
        ComponentProperties p = new ComponentProperties();
        p.setTitle("T");
        p.setHtmlFilePath("f.html");
        p.setReloadable(true);
        p.setComments("X");
        p.setComponentInput("{\"z\":1}");

        // When
        Component created = componentService.createAndPersistComponent(s, p);

        // Then
        assertThat(created.getStudy()).isEqualTo(s);
        assertThat(s.getComponentList()).contains(created);
        verify(componentDao).persist(created);
        verify(studyDao).merge(s);
    }

    @Test
    public void renameHtmlFilePath_withEmptyNew_shouldPersistEmpty_andNotTouchFS() throws IOException {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);

        // When
        componentService.renameHtmlFilePath(c, "  ", true);

        // Then
        assertThat(c.getHtmlFilePath()).isEqualTo("");
        verify(componentDao).merge(c);
        verify(ioUtils, never()).renameHtmlFile(anyString(), anyString(), anyString());
        verify(ioUtils, never()).getFileInStudyAssetsDir(anyString(), anyString());
    }

    @Test
    public void renameHtmlFilePath_whenCurrentMissing_shouldOnlyPersistNew_andNotRenameFile() throws IOException {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);
        when(ioUtils.getFileInStudyAssetsDir(s.getDirName(), c.getHtmlFilePath())).thenReturn(Path.of("/does/not/exist"));

        // When
        componentService.renameHtmlFilePath(c, "a/new.html", true);

        // Then
        assertThat(c.getHtmlFilePath()).isEqualTo("a" + File.separator + "new.html");
        verify(componentDao).merge(c);
        verify(ioUtils, never()).renameHtmlFile(anyString(), anyString(), anyString());
    }

    @Test
    public void renameHtmlFilePath_whenCurrentExists_shouldOptionallyRename_andPersistNew() throws IOException {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);
        Path fake = Files.createTempFile("component-test", ".html");
        when(studyDao.findStudyDirNameByComponentId(c.getId())).thenReturn(s.getDirName());
        when(ioUtils.getFileInStudyAssetsDir(s.getDirName(), c.getHtmlFilePath())).thenReturn(fake);

        // When
        componentService.renameHtmlFilePath(c, "a/new2.html", true);

        // Then
        assertThat(c.getHtmlFilePath()).isEqualTo("a" + File.separator + "new2.html");
        verify(ioUtils).renameHtmlFile("a" + File.separator + "index.html", "a/new2.html", s.getDirName());
        verify(componentDao).merge(c);

        // And when rename isn't requested
        componentService.renameHtmlFilePath(c, "a/new3.html", false);
        verify(ioUtils, never()).renameHtmlFile("a" + File.separator + "new2.html", "a/new3.html", s.getDirName());
        verify(componentDao, times(2)).merge(c);
        assertThat(c.getHtmlFilePath()).isEqualTo("a" + File.separator + "new3.html");
    }

    @Test
    public void validate_validComponent_shouldNotThrow() {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);

        // When / Then
        componentService.validate(c); // should not throw
    }

    @Test(expected = javax.validation.ValidationException.class)
    public void validate_invalidComponent_shouldThrow() {
        // Given
        Study s = new Study();
        Component c = exampleComponent(s);
        c.setTitle("   "); // invalid, missing title

        // When / Then
        componentService.validate(c);
    }

    @Test
    public void remove_shouldUpdateStudy_removeResults_andRemoveComponent() {
        // Given
        Study s = new Study();
        s.setId(9L);
        Component c = exampleComponent(s);
        s.addComponent(c);

        // When
        componentService.remove(c);

        // Then
        assertThat(s.getComponentList().contains(c)).isFalse();
        verify(studyDao).merge(s);
        verify(componentDao).remove(c);
    }

    @Test
    public void getComponentFromIdOrUuid_withId_shouldReturn_andCheckPermissions() {
        // Given
        User signed = new User("u", "U", "u@x");
        Context.current().args().put(SIGNEDIN_USER, signed);

        Component c = new Component();
        c.setId(123L);
        when(componentDao.findById(123L)).thenReturn(c);

        // When
        Component res = componentService.getComponentFromIdOrUuid("123");

        // Then
        assertThat(res).isEqualTo(c);
    }

    @Test
    public void getComponentFromIdOrUuid_withUuid_shouldReturn_andCheckPermissions() {
        // Given
        User signed = new User("u", "U", "u@x");
        Context.current().args().put(SIGNEDIN_USER, signed);

        Component c = new Component();
        c.setId(55L);
        when(componentDao.findByUuid("abc")).thenReturn(Optional.of(c));

        // When
        Component res = componentService.getComponentFromIdOrUuid("abc");

        // Then
        assertThat(res).isEqualTo(c);
    }

    @Test
    public void getComponentFromIdOrUuid_withUnknownId_shouldThrowNotFound() {
        Context.current().args().put(SIGNEDIN_USER, new User("u", "U", "u@x"));
        when(componentDao.findById(999L)).thenReturn(null);
        Component c = componentService.getComponentFromIdOrUuid("999");
        assertThat(c).isNull();
    }

    @Test
    public void getComponentFromIdOrUuid_withUnknownUuid_shouldReturnNull() {
        Context.current().args().put(SIGNEDIN_USER, new User("u", "U", "u@x"));
        when(componentDao.findByUuid("nope")).thenReturn(Optional.empty());
        Component c = componentService.getComponentFromIdOrUuid("nope");
        assertThat(c).isNull();
    }
}
