package services.gui;

import auth.gui.AuthService;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.ValidationException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.gui.StudyProperties;
import org.junit.Before;
import org.junit.Test;
import utils.common.IOUtils;

import java.io.IOException;
import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudyService.
 */
public class StudyServiceTest {

    private BatchService batchService;
    private ComponentService componentService;
    private UserDao userDao;
    private StudyDao studyDao;
    private IOUtils ioUtils;
    private StudyLogger studyLogger;
    private AuthService authService;

    private StudyService studyService;

    @Before
    public void setUp() {
        batchService = mock(BatchService.class);
        componentService = mock(ComponentService.class);
        userDao = mock(UserDao.class);
        studyDao = mock(StudyDao.class);
        ioUtils = mock(IOUtils.class);
        studyLogger = mock(StudyLogger.class);
        authService = mock(AuthService.class);

        studyService = new StudyService(batchService, componentService, studyDao, userDao,
                ioUtils, studyLogger, authService);
    }

    private Study newStudyWithComponents(String title, String dirName, int numberOfComponents) {
        Study s = new Study();
        s.setId(1L);
        s.setUuid(UUID.randomUUID().toString());
        s.setTitle(title);
        s.setDirName(dirName);
        for (int i = 0; i < numberOfComponents; i++) {
            Component c = new Component();
            c.setId((long) (i + 1));
            c.setTitle("C" + (i + 1));
            s.addComponent(c);
        }
        // add default batch for membership logic if needed by tests
        Batch b = new Batch();
        b.setId(1L);
        b.setUuid(UUID.randomUUID().toString());
        s.addBatch(b);
        return s;
    }


    @Test
    public void changeComponentPosition_valid_reorders_andUpdates() throws Exception {
        Study s = newStudyWithComponents("T", "d", 3);
        Component c1 = s.getComponentList().get(0);
        Component c2 = s.getComponentList().get(1);
        Component c3 = s.getComponentList().get(2);

        // move c2 to position 1
        studyService.changeComponentPosition("1", s, c2);

        assertThat(s.getComponentList()).containsExactly(c2, c1, c3);
        verify(studyDao).update(s);
    }

    @Test(expected = BadRequestException.class)
    public void changeComponentPosition_invalidNumber_throws() throws Exception {
        Study s = newStudyWithComponents("T", "d", 2);
        studyService.changeComponentPosition("abc", s, s.getComponentList().get(0));
    }

    @Test(expected = BadRequestException.class)
    public void changeComponentPosition_outOfBounds_throws() throws Exception {
        Study s = newStudyWithComponents("T", "d", 2);
        studyService.changeComponentPosition("5", s, s.getComponentList().get(0));
    }

    @Test
    public void getStudyFromIdOrUuid_numericId_branch() {
        User signedIn = new User("alice", "Alice", "alice@example.org");
        when(authService.getSignedinUser()).thenReturn(signedIn);

        Study s = new Study();
        s.setId(42L);
        when(studyDao.findById(42L)).thenReturn(s);

        Study result = studyService.getStudyFromIdOrUuid("42");

        assertThat(result).isEqualTo(s);
    }

    @Test
    public void getStudyFromIdOrUuid_uuid_branch() {
        User signedIn = new User("bob", "Bob", "bob@example.org");
        when(authService.getSignedinUser()).thenReturn(signedIn);

        Study s = new Study();
        s.setId(5L);
        String uuid = UUID.randomUUID().toString();
        when(studyDao.findByUuid(uuid)).thenReturn(Optional.of(s));

        Study result = studyService.getStudyFromIdOrUuid(uuid);
        assertThat(result).isEqualTo(s);
    }

    @Test
    public void updateStudy_AndRenameAssets_setsLogHashWhenDescriptionChanged() {
        // Given
        Study existing = new Study();
        existing.setId(1L);
        existing.setDescription("old");
        Study updated = new Study();
        updated.setDescription("new");
        User user = new User("u", "U", "u@example.org");

        // When
        studyService.updateStudyAndRenameAssets(existing, updated, user);

        // Then
        verify(studyDao).update(existing);
        verify(studyLogger).logStudyDescriptionHash(existing, user);
    }

    @Test
    public void updateStudy_AndRenameAssets_noLogWhenDescriptionUnchanged() {
        Study existing = new Study();
        existing.setId(1L);
        existing.setDescription("same");
        Study updated = new Study();
        updated.setDescription("same");
        User user = new User("u", "U", "u@example.org");

        studyService.updateStudyAndRenameAssets(existing, updated, user);

        verify(studyDao).update(existing);
        verify(studyLogger, never()).logStudyDescriptionHash(any(), any());
    }

    @Test
    public void updateStudy_AndRenameAssets_withProperties_logsWhenChanged() throws IOException {
        Study s = new Study();
        s.setDescription("old");
        StudyProperties props = new StudyProperties();
        props.setTitle("t");
        props.setDescription("new");
        User user = new User("u", "U", "u@example.org");

        studyService.updateStudyAndRenameAssets(s, props, user);

        verify(studyDao).update(s);
        verify(studyLogger).logStudyDescriptionHash(s, user);
    }

    @Test
    public void clone_clonesProperties_components_andAssetsDir() throws Exception {
        Study original = newStudyWithComponents("My Study", "origDir", 2);
        original.setDescription("desc");
        original.setComments("comments");
        original.setEndRedirectUrl("https://example.org/end");
        original.setStudyEntryMsg("hello");
        original.setStudyInput("{\"k\":1}");
        original.setLocked(true);
        original.setGroupStudy(true);
        original.setLinearStudy(true);
        original.setAllowPreview(true);

        Component origC1 = original.getComponentList().get(0);
        Component origC2 = original.getComponentList().get(1);

        Component clonedC1 = new Component();
        clonedC1.setUuid(UUID.randomUUID().toString());
        Component clonedC2 = new Component();
        clonedC2.setUuid(UUID.randomUUID().toString());

        when(componentService.clone(origC1)).thenReturn(clonedC1);
        when(componentService.clone(origC2)).thenReturn(clonedC2);

        when(studyDao.findByTitle("My Study (clone)")).thenReturn(Collections.emptyList());
        when(ioUtils.cloneStudyAssetsDirectory("origDir")).thenReturn("newDir");

        Study clone = studyService.clone(original);

        assertThat(clone.getUuid()).isNotEqualTo(original.getUuid());
        assertThat(clone.getTitle()).isEqualTo("My Study (clone)");
        assertThat(clone.getDirName()).isEqualTo("newDir");

        assertThat(clone.getDescription()).isEqualTo("desc");
        assertThat(clone.getComments()).isEqualTo("comments");
        assertThat(clone.getEndRedirectUrl()).isEqualTo("https://example.org/end");
        assertThat(clone.getStudyEntryMsg()).isEqualTo("hello");
        assertThat(clone.getStudyInput()).isEqualTo("{\"k\":1}");

        // clone is always unlocked
        assertThat(clone.isLocked()).isFalse();

        assertThat(clone.isGroupStudy()).isTrue();
        assertThat(clone.isLinearStudy()).isTrue();
        assertThat(clone.isAllowPreview()).isTrue();

        assertThat(clone.getComponentList()).containsExactly(clonedC1, clonedC2);
        assertThat(clonedC1.getStudy()).isEqualTo(clone);
        assertThat(clonedC2.getStudy()).isEqualTo(clone);

        verify(componentService).clone(origC1);
        verify(componentService).clone(origC2);
        verify(ioUtils).cloneStudyAssetsDirectory("origDir");
    }

    @Test
    public void cloneTitle_incrementsNumber_ifTitleAlreadyExists() throws Exception {
        Study original = newStudyWithComponents("My Study", "origDir", 0);

        // The first candidate exists, the second exists, the third is free
        when(studyDao.findByTitle("My Study (clone)")).thenReturn(Collections.singletonList(new Study()));
        when(studyDao.findByTitle("My Study (clone 2)")).thenReturn(Collections.singletonList(new Study()));
        when(studyDao.findByTitle("My Study (clone 3)")).thenReturn(Collections.emptyList());
        when(ioUtils.cloneStudyAssetsDirectory("origDir")).thenReturn("newDir");

        Study clone = studyService.clone(original);

        assertThat(clone.getTitle()).isEqualTo("My Study (clone 3)");
        verify(studyDao).findByTitle("My Study (clone)");
        verify(studyDao).findByTitle("My Study (clone 2)");
        verify(studyDao).findByTitle("My Study (clone 3)");
    }

    @Test
    public void changeUserMember_addsMember_andAddsWorkerToBatches_andPersists() throws Exception {
        Study study = new Study();
        Batch b = mock(Batch.class);
        study.addBatch(b);

        User user = mock(User.class);
        JatosWorker worker = mock(JatosWorker.class);
        when(user.getWorker()).thenReturn(worker);

        // precondition: user not in set
        assertThat(study.getUserList().contains(user)).isFalse();

        studyService.changeUserMember(study, user, true);

        assertThat(study.getUserList().contains(user)).isTrue();
        verify(b).addWorker(worker);
        verify(studyDao).update(study);
        verify(userDao).update(user);
    }

    @Test
    public void changeUserMember_addMember_noopIfAlreadyMember() throws Exception {
        Study study = new Study();
        User user = mock(User.class);
        study.addUser(user);

        studyService.changeUserMember(study, user, true);

        verify(studyDao, never()).update(any());
        verify(userDao, never()).update(any());
    }

    @Test
    public void changeUserMember_removesMember_andRemovesWorkerFromBatches_andPersists() throws Exception {
        Study study = new Study();
        User remaining = mock(User.class);
        User toRemove = mock(User.class);
        JatosWorker worker = mock(JatosWorker.class);
        when(toRemove.getWorker()).thenReturn(worker);

        // ensure size > 1 to allow removal
        study.addUser(remaining);
        study.addUser(toRemove);

        Batch b = mock(Batch.class);
        study.addBatch(b);

        studyService.changeUserMember(study, toRemove, false);

        assertThat(study.getUserList().contains(toRemove)).isFalse();
        verify(b).removeWorker(worker);
        verify(studyDao).update(study);
        verify(userDao).update(toRemove);
    }

    @Test(expected = ForbiddenException.class)
    public void changeUserMember_removeLastMember_throwsForbidden() throws Exception {
        Study study = new Study();
        User onlyUser = mock(User.class);
        study.addUser(onlyUser);

        studyService.changeUserMember(study, onlyUser, false);
    }

    @Test
    public void addAllUserMembers_addsUsers_andWorkersToBatches_andPersists() {
        Study study = new Study();

        Batch b = mock(Batch.class);
        study.addBatch(b);

        User u1 = mock(User.class);
        User u2 = mock(User.class);
        JatosWorker w1 = mock(JatosWorker.class);
        JatosWorker w2 = mock(JatosWorker.class);
        when(u1.getWorker()).thenReturn(w1);
        when(u2.getWorker()).thenReturn(w2);

        when(userDao.findAll()).thenReturn(Arrays.asList(u1, u2));

        studyService.addAllUserMembers(study);

        assertThat(study.getUserList()).contains(u1, u2);
        verify(b).addAllWorkers(Arrays.asList(w1, w2));
        verify(studyDao).update(study);
        verify(userDao).update(u1);
        verify(userDao).update(u2);
    }

    @Test
    public void removeAllUserMembers_keepsSignedInUser_removesOthers_andPersists() {
        Study study = new Study();

        Batch b = mock(Batch.class);
        study.addBatch(b);

        User signedIn = mock(User.class);
        User other = mock(User.class);
        JatosWorker otherWorker = mock(JatosWorker.class);
        when(other.getWorker()).thenReturn(otherWorker);

        // Make a study contain both users beforehand
        study.addUser(signedIn);
        study.addUser(other);

        when(authService.getSignedinUser()).thenReturn(signedIn);
        when(userDao.findAll()).thenReturn(new ArrayList<>(Arrays.asList(signedIn, other)));

        studyService.removeAllUserMembers(study);

        assertThat(study.getUserList()).contains(signedIn);
        assertThat(study.getUserList()).excludes(other);

        verify(b).removeAllWorkers(Collections.singletonList(otherWorker));
        verify(other).removeStudy(study);

        verify(studyDao).update(study);
        verify(userDao, never()).update(signedIn);
        verify(userDao).update(other);
    }

    @Test
    public void createAndPersistStudyAndAssetsDir_setsDirNameToUuid_ifMissing_createsDir_andPersists() throws Exception {
        StudyService spy = spy(studyService);

        StudyProperties props = new StudyProperties();
        props.setTitle("t");
        props.setDescription("d");
        props.setDirName(null);

        User user = mock(User.class);

        // We don't care about the internals of createAndPersistStudy in this test
        doAnswer(inv -> inv.getArgument(1)).when(spy).createAndPersistStudy(any(), any(Study.class));

        Study created = spy.createAndPersistStudyAndAssetsDir(user, props, true);

        assertThat(created.getDirName()).isNotEmpty();
        verify(ioUtils).createStudyAssetsDir(created.getDirName());
        verify(spy).createAndPersistStudy(user, created);
    }

    @Test
    public void createAndPersistStudy_createsDefaultBatch_whenNoBatches_addsUser_updatesAndLogs() {
        Study study = new Study();
        study.setUuid(UUID.randomUUID().toString());
        study.setTitle("t");
        study.setDescription("desc");
        study.setDirName("dir");

        User user = new User("alice", "Alice", "alice@example.org");
        when(userDao.findByUsername("alice")).thenReturn(user);

        Batch defaultBatch = mock(Batch.class);
        when(batchService.createDefaultBatch()).thenReturn(defaultBatch);

        Study returned = studyService.createAndPersistStudy(user, study);

        assertThat(returned).isSameAs(study);

        verify(studyDao).create(study);
        verify(batchService).createDefaultBatch();
        verify(batchService).initBatch(defaultBatch, study);

        verify(studyLogger).create(study);
        verify(studyLogger).log(study, user, "Created study");
        verify(studyLogger).logStudyDescriptionHash(study, user);
    }

    @Test
    public void createAndPersistStudy_usesProvidedBatches_whenAlreadyPresent() {
        Study study = new Study();
        study.setUuid(UUID.randomUUID().toString());
        study.setTitle("t");
        study.setDescription(null); // no description hash logging
        study.setDirName("dir");

        Batch b1 = mock(Batch.class);
        Batch b2 = mock(Batch.class);
        study.addBatch(b1);
        study.addBatch(b2);

        User user = new User("alice", "Alice", "alice@example.org");
        when(userDao.findByUsername("alice")).thenReturn(user);

        studyService.createAndPersistStudy(user, study);

        verify(studyDao).create(study);
        verify(batchService).initBatch(b1, study);
        verify(batchService).initBatch(b2, study);

        verify(studyLogger).create(study);
        verify(studyLogger).log(study, user, "Created study");

        verify(studyLogger, never()).logStudyDescriptionHash(any(), any());
        verifyNoMoreInteractions(studyLogger);
    }

    @Test
    public void updateStudyWithoutDirName_updatesFields_butKeepsDirName_andLogsWhenHashChanged() {
        Study existing = new Study();
        existing.setDirName("keepMe");
        existing.setDescription("old");

        Study updated = new Study();
        updated.setDirName("newDirShouldBeIgnored");
        updated.setDescription("new");

        User user = mock(User.class);

        studyService.updateStudyWithoutDirName(existing, updated, user);

        assertThat(existing.getDirName()).isEqualTo("keepMe");
        assertThat(existing.getDescription()).isEqualTo("new");
        verify(studyDao).update(existing);
        verify(studyLogger).logStudyDescriptionHash(existing, user);
    }

    @Test
    public void updateDescription_logsOnlyIfChanged() {
        Study study = new Study();
        study.setDescription("same");
        User user = mock(User.class);

        studyService.updateDescription(study, "same", user);
        verify(studyDao).update(study);
        verify(studyLogger, never()).logStudyDescriptionHash(any(), any());

        reset(studyDao, studyLogger);

        study.setDescription("old");
        studyService.updateDescription(study, "new", user);
        verify(studyDao).update(study);
        verify(studyLogger).logStudyDescriptionHash(study, user);
    }

    @Test
    public void bindToStudy_copiesAllFieldsFromProperties() {
        Study study = new Study();
        StudyProperties props = new StudyProperties();
        props.setTitle("t");
        props.setDescription("d");
        props.setComments("c");
        props.setDirName("dir");
        props.setStudyEntryMsg("msg");
        props.setEndRedirectUrl("url");
        props.setStudyInput("{}");
        props.setLocked(true);
        props.setActive(true);
        props.setGroupStudy(true);
        props.setLinearStudy(true);
        props.setAllowPreview(true);

        studyService.bindToStudy(study, props);

        assertThat(study.getTitle()).isEqualTo("t");
        assertThat(study.getDescription()).isEqualTo("d");
        assertThat(study.getComments()).isEqualTo("c");
        assertThat(study.getDirName()).isEqualTo("dir");
        assertThat(study.getStudyEntryMsg()).isEqualTo("msg");
        assertThat(study.getEndRedirectUrl()).isEqualTo("url");
        assertThat(study.getStudyInput()).isEqualTo("{}");
        assertThat(study.isLocked()).isTrue();
        assertThat(study.isActive()).isTrue();
        assertThat(study.isGroupStudy()).isTrue();
        assertThat(study.isLinearStudy()).isTrue();
        assertThat(study.isAllowPreview()).isTrue();
    }

    @Test
    public void renameStudyAssetsDir_renamesInFs_updatesStudy_andPersists() throws Exception {
        Study study = new Study();
        study.setDirName("oldDir");

        studyService.renameStudyAssetsDir(study, "newDir");

        verify(ioUtils).renameStudyAssetsDir("oldDir", "newDir");
        assertThat(study.getDirName()).isEqualTo("newDir");
        verify(studyDao).update(study);
    }

    @Test
    public void bindToProperties_copiesFieldsFromStudy() {
        Study study = new Study();
        study.setId(7L);
        study.setUuid("uuid");
        study.setTitle("t");
        study.setDescription("d");
        study.setLocked(true);
        study.setGroupStudy(true);
        study.setLinearStudy(false);
        study.setAllowPreview(true);
        study.setDirName("dir");
        study.setComments("c");
        study.setEndRedirectUrl("url");
        study.setStudyEntryMsg("msg");
        study.setStudyInput("{}");

        StudyProperties props = studyService.bindToProperties(study);

        assertThat(props.getStudyId()).isEqualTo(7L);
        assertThat(props.getUuid()).isEqualTo("uuid");
        assertThat(props.getTitle()).isEqualTo("t");
        assertThat(props.getDescription()).isEqualTo("d");
        assertThat(props.isLocked()).isTrue();
        assertThat(props.isGroupStudy()).isTrue();
        assertThat(props.isLinearStudy()).isFalse();
        assertThat(props.isAllowPreview()).isTrue();
        assertThat(props.getDirName()).isEqualTo("dir");
        assertThat(props.getComments()).isEqualTo("c");
        assertThat(props.getEndRedirectUrl()).isEqualTo("url");
        assertThat(props.getStudyEntryMsg()).isEqualTo("msg");
        assertThat(props.getStudyInput()).isEqualTo("{}");
    }

    @Test
    public void validate_validStudy_doesNotThrow() throws Exception {
        Study study = new Study();
        study.setTitle("t");
        study.setDirName("dir");
        study.setUuid(UUID.randomUUID().toString());

        // If StudyProperties has additional required fields, this test might need adaptation
        studyService.validate(study);
    }

    @Test(expected = ValidationException.class)
    public void validate_invalidStudy_throws() throws Exception {
        Study study = new Study();
        // Intentionally omit title/uuid/etc. to make validation fail
        studyService.validate(study);
    }

    @Test
    public void removeStudyInclAssets_removesBatches_users_study_assets_andLogs() throws Exception {
        Study study = new Study();
        study.setDirName("dir");

        Batch b1 = mock(Batch.class);
        Batch b2 = mock(Batch.class);
        study.addBatch(b1);
        study.addBatch(b2);

        User u1 = mock(User.class);
        User u2 = mock(User.class);
        study.addUser(u1);
        study.addUser(u2);

        User signedIn = mock(User.class);

        studyService.removeStudyInclAssets(study, signedIn);

        verify(batchService).remove(b1, signedIn);
        verify(batchService).remove(b2, signedIn);

        verify(studyDao).remove(study);
        verify(ioUtils).removeStudyAssetsDir("dir");

        verify(studyLogger).log(study, signedIn, "Removed study");
        verify(studyLogger).retire(study);
    }

    @Test
    public void removeStudyInclAssets_doesNotRemoveAssets_whenDirNameNull() throws Exception {
        Study study = new Study();
        study.setDirName(null);

        User u1 = mock(User.class);
        study.addUser(u1);

        User signedIn = mock(User.class);

        studyService.removeStudyInclAssets(study, signedIn);

        verify(studyDao).remove(study);
        verify(ioUtils, never()).removeStudyAssetsDir(anyString());
        verify(studyLogger).retire(study);
    }

}