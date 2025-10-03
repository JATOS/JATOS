package services.gui;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.ForbiddenException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.Worker;
import org.junit.Before;
import org.junit.Test;
import play.data.validation.ValidationError;
import testutils.common.ContextMocker;
import utils.common.IOUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ResultRemover}
 *
 * @author Kristian Lange
 */
public class ResultRemoverTest {

    private static org.mockito.MockedStatic<general.common.Common> commonStatic;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @org.junit.BeforeClass
    public static void initCommonStatics() {
        String tmp = System.getProperty("java.io.tmpdir") + java.io.File.separator + "jatos-test";
        commonStatic = org.mockito.Mockito.mockStatic(general.common.Common.class);
        commonStatic.when(general.common.Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(general.common.Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonStatic.when(general.common.Common::getResultUploadsPath).thenReturn(tmp);
        commonStatic.when(general.common.Common::isStudyLogsEnabled).thenReturn(false);
    }

    @org.junit.AfterClass
    public static void tearDownCommonStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    private Checker checker;
    private ComponentResultDao componentResultDao;
    private StudyResultDao studyResultDao;
    private GroupResultDao groupResultDao;
    private WorkerDao workerDao;
    private StudyLogger studyLogger;
    private IOUtils ioUtils;

    private ResultRemover resultRemover;

    private User user;
    private Study study;
    private Batch batch;
    private Component component;
    private Worker worker;

    @Before
    public void setup() {
        ContextMocker.mock();
        checker = mock(Checker.class);
        componentResultDao = mock(ComponentResultDao.class);
        studyResultDao = mock(StudyResultDao.class);
        groupResultDao = mock(GroupResultDao.class);
        workerDao = mock(WorkerDao.class);
        studyLogger = mock(StudyLogger.class);
        ioUtils = mock(IOUtils.class);

        resultRemover = new ResultRemover(checker, componentResultDao, studyResultDao, groupResultDao, workerDao, studyLogger, ioUtils);

        // Minimal model graph used by several tests
        user = newUser();
        study = newStudy(user);
        batch = newBatch(study);
        component = newComponent(study);
        worker = newWorker();
    }

    private User newUser() {
        User user = new User();
        user.setUsername("tester");
        user.setName("Tester");
        return user;
    }

    private Study newStudy(User user) {
        Study study = new Study();
        study.setId(10L);
        study.setTitle("S");
        study.addUser(user);
        return study;
    }

    private Batch newBatch(Study study) {
        Batch batch = new Batch();
        batch.setId(20L);
        batch.setStudy(study);
        return batch;
    }

    private Component newComponent(Study study) {
        Component component = new Component();
        component.setStudy(study);
        component.setId(30L);
        return component;
    }

    private Worker newWorker() {
        Worker worker = new Worker() {
            @Override
            public String generateConfirmationCode() {
                return "code";
            }

            @Override
            public List<ValidationError> validate() {
                return Collections.emptyList();
            }

            @Override
            public String getWorkerType() {
                return "TEST";
            }

            @Override
            public String getUIWorkerType() {
                return "TEST";
            }
        };
        worker.setId(40L);
        return worker;
    }

    private StudyResult newStudyResult(long id) {
        StudyResult sr = new StudyResult();
        sr.setId(id);
        sr.setStudy(study);
        sr.setBatch(batch);
        sr.setWorker(worker);
        return sr;
    }

    private ComponentResult newComponentResult(long id, StudyResult sr) {
        ComponentResult cr = new ComponentResult(component);
        cr.setId(id);
        cr.setStudyResult(sr);
        sr.addComponentResult(cr);
        return cr;
    }

    @Test
    public void removeComponentResults_shouldRemoveEach_andLog_andRemoveEmptyStudyResultWhenFlagTrue() throws Exception {
        StudyResult sr = newStudyResult(100L);
        ComponentResult cr1 = newComponentResult(200L, sr);
        ComponentResult cr2 = newComponentResult(201L, sr);

        when(componentResultDao.findByIds(Arrays.asList(200L, 201L))).thenReturn(Arrays.asList(cr1, cr2));
        when(componentResultDao.findById(200L)).thenReturn(cr1);
        when(componentResultDao.findById(201L)).thenReturn(cr2);

        resultRemover.removeComponentResults(Arrays.asList(200L, 201L), user, true);

        // studyResult should have both removed, uploads dir removed twice, and component results removed
        verify(studyResultDao, atLeastOnce()).update(sr);
        verify(ioUtils, times(1)).removeResultUploadsDir(eq(sr.getId()), eq(200L));
        verify(ioUtils, times(1)).removeResultUploadsDir(eq(sr.getId()), eq(201L));
        verify(componentResultDao).remove(cr1);
        verify(componentResultDao).remove(cr2);

        // since empty after removing both, removeEmptyStudyResult should remove sr
        verify(workerDao).update(worker);
        verify(studyResultDao).remove(sr);

        // one log entry per study
        verify(studyLogger).log(eq(study), eq(user), contains("Removed result data and files"));

        // permission check invoked
        verify(checker).checkComponentResults(anyList(), eq(user), eq(true));
    }

    @Test
    public void removeComponentResults_whenNotEmpty_shouldNotRemoveStudyResult() throws Exception {
        StudyResult sr = newStudyResult(101L);
        ComponentResult cr1 = newComponentResult(210L, sr);
        // another component that is not in the input list keeps the StudyResult non-empty
        ComponentResult other = newComponentResult(211L, sr);

        when(componentResultDao.findByIds(Collections.singletonList(210L))).thenReturn(Collections.singletonList(cr1));
        when(componentResultDao.findById(210L)).thenReturn(cr1);

        resultRemover.removeComponentResults(Collections.singletonList(210L), user, true);

        // Only the specified component removed, and sr not removed
        verify(componentResultDao).remove(cr1);
        verify(studyResultDao, atLeastOnce()).update(sr);
        verify(studyResultDao, never()).remove(sr);
        verify(ioUtils).removeResultUploadsDir(eq(sr.getId()), eq(210L));
    }

    @Test(expected = ForbiddenException.class)
    public void removeComponentResults_shouldPropagateForbidden() throws Exception {
        StudyResult sr = newStudyResult(102L);
        ComponentResult cr1 = newComponentResult(220L, sr);
        when(componentResultDao.findByIds(Collections.singletonList(220L))).thenReturn(Collections.singletonList(cr1));
        doThrow(new ForbiddenException("no")).when(checker).checkComponentResults(anyList(), any(User.class), anyBoolean());

        resultRemover.removeComponentResults(Collections.singletonList(220L), user, true);
    }

    @Test
    public void removeStudyResults_shouldRemoveAll_andLog() throws Exception {
        StudyResult sr1 = newStudyResult(300L);
        StudyResult sr2 = newStudyResult(301L);
        ComponentResult cr1 = newComponentResult(400L, sr1);
        ComponentResult cr2 = newComponentResult(401L, sr2);

        when(studyResultDao.findByIds(Arrays.asList(300L, 301L))).thenReturn(Arrays.asList(sr1, sr2));
        when(studyResultDao.findById(300L)).thenReturn(sr1);
        when(studyResultDao.findById(301L)).thenReturn(sr2);

        resultRemover.removeStudyResults(Arrays.asList(300L, 301L), user);

        // all component results of each study result removed via dao.remove
        verify(componentResultDao).remove(cr1);
        verify(componentResultDao).remove(cr2);

        // uploads dir removed per study result and study result removed
        verify(ioUtils).removeResultUploadsDir(300L);
        verify(ioUtils).removeResultUploadsDir(301L);
        verify(studyResultDao).remove(sr1);
        verify(studyResultDao).remove(sr2);

        // log once per study
        verify(studyLogger).log(eq(study), eq(user), contains("Removed result data and files"));

        // permissions checked
        verify(checker).checkStudyResults(anyList(), eq(user), eq(true));
    }

    @Test
    public void removeAllComponentResults_shouldDetachFromStudyResult_thenRemove_andLog() throws Exception {
        StudyResult sr = newStudyResult(500L);
        ComponentResult cr1 = newComponentResult(600L, sr);
        ComponentResult cr2 = newComponentResult(601L, sr);

        when(componentResultDao.findAllByComponent(component)).thenReturn(Arrays.asList(cr1, cr2));
        when(componentResultDao.findById(600L)).thenReturn(cr1);
        when(componentResultDao.findById(601L)).thenReturn(cr2);

        resultRemover.removeAllComponentResults(component, user);

        // studyResult updated (removed both component results from list)
        verify(studyResultDao, atLeastOnce()).update(sr);
        // both removed via dao
        verify(componentResultDao).remove(cr1);
        verify(componentResultDao).remove(cr2);
        // uploads directories removed for each component
        verify(ioUtils).removeResultUploadsDir(eq(sr.getId()), eq(600L));
        verify(ioUtils).removeResultUploadsDir(eq(sr.getId()), eq(601L));
        // log once
        verify(studyLogger).log(eq(study), eq(user), contains("Removed result data and files"));
    }

    @Test
    public void removeAllStudyResults_shouldRemoveEach_andLog() throws Exception {
        StudyResult sr1 = newStudyResult(700L);
        StudyResult sr2 = newStudyResult(701L);
        when(studyResultDao.findAllByBatch(batch)).thenReturn(Arrays.asList(sr1, sr2));
        when(studyResultDao.findById(700L)).thenReturn(sr1);
        when(studyResultDao.findById(701L)).thenReturn(sr2);

        // add some component results to each
        ComponentResult cr1 = newComponentResult(800L, sr1);
        ComponentResult cr2 = newComponentResult(801L, sr2);

        resultRemover.removeAllStudyResults(batch, user);

        // components removed
        verify(componentResultDao).remove(cr1);
        verify(componentResultDao).remove(cr2);
        // uploads for sr and studyResult removed
        verify(ioUtils).removeResultUploadsDir(700L);
        verify(ioUtils).removeResultUploadsDir(701L);
        verify(studyResultDao).remove(sr1);
        verify(studyResultDao).remove(sr2);
        // log once
        verify(studyLogger).log(eq(study), eq(user), contains("Removed result data and files"));
    }

}
