package services.gui;

import auth.gui.AuthService;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.NotFoundException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudyService.
 */
public class StudyServiceTest {

    private StudyDao studyDao;
    private StudyLogger studyLogger;
    private AuthService authService;
    private Checker checker;

    private StudyService studyService;

    @Before
    public void setUp() {
        BatchService batchService = mock(BatchService.class);
        ComponentService componentService = mock(ComponentService.class);
        BatchDao batchDao = mock(BatchDao.class);
        UserDao userDao = mock(UserDao.class);
        WorkerDao workerDao = mock(WorkerDao.class);
        studyDao = mock(StudyDao.class);
        studyLogger = mock(StudyLogger.class);
        authService = mock(AuthService.class);
        checker = mock(Checker.class);

        studyService = new StudyService(batchService, componentService, studyDao, batchDao, userDao, workerDao,
                null, studyLogger, authService, checker);
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
            c.setStudy(s);
            s.addComponent(c);
        }
        // add default batch for membership logic if needed by tests
        Batch b = new Batch();
        b.setId(1L);
        b.setUuid(UUID.randomUUID().toString());
        b.setStudy(s);
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
    public void getStudyFromIdOrUuid_numericId_branch_checksPermission_andReturns() throws Exception {
        User signedIn = new User("alice", "Alice", "alice@example.org");
        when(authService.getSignedinUser()).thenReturn(signedIn);

        Study s = new Study();
        s.setId(42L);
        when(studyDao.findById(42L)).thenReturn(s);

        Study result = studyService.getStudyFromIdOrUuid("42");

        assertThat(result).isEqualTo(s);
        verify(checker).checkStandardForStudy(s, 42L, signedIn);
    }

    @Test
    public void getStudyFromIdOrUuid_uuid_branch_checksPermission_andReturns() throws Exception {
        User signedIn = new User("bob", "Bob", "bob@example.org");
        when(authService.getSignedinUser()).thenReturn(signedIn);

        Study s = new Study();
        s.setId(5L);
        String uuid = UUID.randomUUID().toString();
        when(studyDao.findByUuid(uuid)).thenReturn(Optional.of(s));

        Study result = studyService.getStudyFromIdOrUuid(uuid);
        assertThat(result).isEqualTo(s);
        verify(checker).checkStandardForStudy(s, 5L, signedIn);
    }

    @Test(expected = NotFoundException.class)
    public void getStudyFromIdOrUuid_numeric_notFound_throws() throws Exception {
        when(authService.getSignedinUser()).thenReturn(new User("u", "U", "u@example.org"));
        when(studyDao.findById(99L)).thenReturn(null);
        studyService.getStudyFromIdOrUuid("99");
    }

    @Test(expected = NotFoundException.class)
    public void getStudyFromIdOrUuid_uuid_notFound_throws() throws Exception {
        when(authService.getSignedinUser()).thenReturn(new User("u", "U", "u@example.org"));
        when(studyDao.findByUuid("nope")).thenReturn(Optional.empty());
        studyService.getStudyFromIdOrUuid("nope");
    }

    @Test
    public void updateStudy_setsLogHashWhenDescriptionChanged() {
        // Given
        Study existing = new Study();
        existing.setId(1L);
        existing.setDescription("old");
        Study updated = new Study();
        updated.setDescription("new");
        User user = new User("u", "U", "u@example.org");

        // When
        studyService.updateStudy(existing, updated, user);

        // Then
        verify(studyDao).update(existing);
        verify(studyLogger).logStudyDescriptionHash(existing, user);
    }

    @Test
    public void updateStudy_noLogWhenDescriptionUnchanged() {
        Study existing = new Study();
        existing.setId(1L);
        existing.setDescription("same");
        Study updated = new Study();
        updated.setDescription("same");
        User user = new User("u", "U", "u@example.org");

        studyService.updateStudy(existing, updated, user);

        verify(studyDao).update(existing);
        verify(studyLogger, never()).logStudyDescriptionHash(any(), any());
    }

    @Test
    public void updateStudy_withProperties_logsWhenChanged() {
        Study s = new Study();
        s.setDescription("old");
        StudyProperties props = new StudyProperties();
        props.setTitle("t");
        props.setDescription("new");
        User user = new User("u", "U", "u@example.org");

        studyService.updateStudy(s, props, user);

        verify(studyDao).update(s);
        verify(studyLogger).logStudyDescriptionHash(s, user);
    }

}