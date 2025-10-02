package services.gui;

import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.*;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import utils.common.Helpers;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Unit tests for Checker.
 *
 * @author Kristian Lange
 */
public class CheckerTest {

    private MockedStatic<Helpers> helpersMock;

    @After
    public void tearDown() {
        if (helpersMock != null) helpersMock.close();
    }

    private Study newStudy(long id) {
        Study study = new Study();
        study.setId(id);
        study.setUuid(UUID.randomUUID().toString());
        return study;
    }

    private User newUser(String username) {
        return new User(username, username, username + "@example.org");
    }

    private long batchIdCounter = 1;
    private Batch newBatch(Study study) {
        Batch batch = new Batch();
        batch.setUuid(UUID.randomUUID().toString());
        batch.setId(batchIdCounter++); // ensure non-null unique ID for equals()
        // wire bi-directional relation
        batch.setStudy(study);
        study.addBatch(batch);
        return batch;
    }

    private Component newComponent(Study study, long id) {
        Component c = new Component();
        c.setId(id);
        c.setStudy(study);
        return c;
    }

    private GroupResult newGroupResult(Batch batch, long id) {
        GroupResult gr = new GroupResult();
        // minimal fields
        gr.setBatch(batch);
        // set an ID if needed for messages/equals
        gr.setId(id);
        return gr;
    }

    private Worker newWorker(long id) {
        JatosWorker w = new JatosWorker();
        w.setId(id);
        return w;
    }

    private StudyResult newStudyResult(Study study) {
        StudyResult sr = new StudyResult();
        sr.setStudy(study);
        return sr;
    }

    private ComponentResult newComponentResult(Component component) {
        ComponentResult cr = new ComponentResult();
        cr.setComponent(component);
        return cr;
    }

    @Test
    public void checkStandardForComponent_byIds_valid() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Component c = newComponent(s, 10L);

        checker.checkStandardForComponent(1L, 10L, c); // should not throw
    }

    @Test(expected = NotFoundException.class)
    public void checkStandardForComponent_byIds_nullComponent_throwsNotFound() throws Exception {
        new Checker().checkStandardForComponent(1L, 10L, null);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForComponent_byIds_componentWithoutStudy_throwsForbidden() throws Exception {
        Component c = new Component();
        c.setId(10L);
        c.setStudy(null);
        new Checker().checkStandardForComponent(1L, 10L, c);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForComponent_byIds_componentBelongsToDifferentStudy_throwsForbidden() throws Exception {
        Study s2 = newStudy(2L);
        Component c = newComponent(s2, 10L);
        new Checker().checkStandardForComponent(1L, 10L, c);
    }

    @Test
    public void checkStandardForComponent_withUser_memberOrSuperuser() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Component c = newComponent(s, 10L);
        User user = newUser("john");
        s.addUser(user);

        checker.checkStandardForComponent(10L, c, user); // member succeeds

        // not a member but superuser allowed
        Study s2 = newStudy(2L);
        Component c2 = newComponent(s2, 11L);
        User other = newUser("alice");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(other)).thenReturn(true);
        checker.checkStandardForComponent(11L, c2, other); // superuser succeeds
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForComponent_withUser_notMemberAndNotSuperuser_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Component c = newComponent(s, 10L);
        User user = newUser("john");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(user)).thenReturn(false);
        checker.checkStandardForComponent(10L, c, user);
    }

    @Test
    public void checkComponentBelongsToStudy_acceptsIdOrUuid() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(42L);
        Component c = newComponent(s, 7L);
        checker.checkComponentBelongsToStudy(c, "42");
        checker.checkComponentBelongsToStudy(c, s.getUuid()); // should not throw
    }

    @Test(expected = ForbiddenException.class)
    public void checkComponentBelongsToStudy_mismatch_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(42L);
        Component c = newComponent(s, 7L);
        checker.checkComponentBelongsToStudy(c, "999");
    }

    @Test
    public void checkStandardForBatch_valid_forStudyAndUser() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Batch b = newBatch(s);

        // Study has batch
        checker.checkStandardForBatch(b, s, 1L);

        // User is member
        User u = newUser("u1");
        s.addUser(u);
        checker.checkStandardForBatch(b, 1L, u);
    }

    @Test(expected = NotFoundException.class)
    public void checkStandardForBatch_nullBatch_throwsNotFound() throws Exception {
        new Checker().checkStandardForBatch(null, newStudy(1L), 1L);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForBatch_batchNotInStudy_throwsForbidden() throws Exception {
        Study s1 = newStudy(1L);
        Study s2 = newStudy(2L);
        Batch bNotInS1 = newBatch(s2);
        new Checker().checkStandardForBatch(bNotInS1, s1, 1L);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForBatch_userNotMemberAndNotSuperuser_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Batch b = newBatch(s);
        User u = newUser("u1");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(u)).thenReturn(false);
        checker.checkStandardForBatch(b, 1L, u);
    }

    @Test
    public void checkStandardForGroup_valid_forStudyAndUser() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Batch b = newBatch(s);
        GroupResult gr = newGroupResult(b, 100L);

        checker.checkStandardForGroup(gr, s, 100L);

        User u = newUser("bob");
        s.addUser(u);
        checker.checkStandardForGroup(gr, 100L, u);
    }

    @Test(expected = NotFoundException.class)
    public void checkStandardForGroup_null_throwsNotFound() throws Exception {
        new Checker().checkStandardForGroup(null, newStudy(1L), 1L);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForGroup_notInStudy_throwsForbidden() throws Exception {
        Study s1 = newStudy(1L);
        Study s2 = newStudy(2L);
        Batch b2 = newBatch(s2);
        GroupResult gr = newGroupResult(b2, 100L);
        new Checker().checkStandardForGroup(gr, s1, 100L);
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForGroup_userNotMemberAndNotSuperuser_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        Batch b = newBatch(s);
        GroupResult gr = newGroupResult(b, 100L);
        User u = newUser("joe");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(u)).thenReturn(false);
        checker.checkStandardForGroup(gr, 100L, u);
    }

    @Test(expected = ForbiddenException.class)
    public void checkDefaultBatch_default_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        // First batch added becomes default according to Study.getDefaultBatch()
        Batch defaultBatch = newBatch(s);
        checker.checkDefaultBatch(defaultBatch);
    }

    @Test
    public void checkDefaultBatch_nonDefault_ok() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        // First batch is default
        Batch defaultBatch = newBatch(s);
        Batch other = newBatch(s);
        checker.checkDefaultBatch(other); // should not throw
    }

    @Test(expected = ForbiddenException.class)
    public void checkStudyLocked_locked_throwsForbidden() throws Exception {
        Study s = newStudy(1L);
        s.setLocked(true);
        new Checker().checkStudyLocked(s);
    }

    @Test
    public void checkStudyLocked_unlocked_ok() throws Exception {
        Study s = newStudy(1L);
        s.setLocked(false);
        new Checker().checkStudyLocked(s);
    }

    @Test
    public void checkStandardForStudy_memberOrSuperuser_ok() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        User u = newUser("mem");
        s.addUser(u);
        checker.checkStandardForStudy(s, 1L, u);

        User su = newUser("su");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(su)).thenReturn(true);
        checker.checkStandardForStudy(s, 1L, su);
    }

    @Test(expected = NotFoundException.class)
    public void checkStandardForStudy_nullStudy_throwsNotFound() throws Exception {
        new Checker().checkStandardForStudy(null, 1L, newUser("u"));
    }

    @Test(expected = ForbiddenException.class)
    public void checkStandardForStudy_notMember_throwsForbidden() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        User u = newUser("x");
        helpersMock = Mockito.mockStatic(Helpers.class);
        helpersMock.when(() -> Helpers.isAllowedSuperuser(u)).thenReturn(false);
        checker.checkStandardForStudy(s, 1L, u);
    }

    @Test
    public void checkComponentResult_andList_ok_andLockedCheck() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        User u = newUser("member");
        s.addUser(u);
        Component comp = newComponent(s, 5L);
        ComponentResult cr = newComponentResult(comp);

        // unlocked, must not be locked true -> fine
        s.setLocked(false);
        checker.checkComponentResult(cr, u, true);

        // list version
        checker.checkComponentResults(Collections.singletonList(cr), u, true);

        // locked study triggers forbidden when studyMustNotBeLocked=true
        s.setLocked(true);
        boolean threw = false;
        try {
            checker.checkComponentResult(cr, u, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();

        // but allowed when flag is false
        checker.checkComponentResult(cr, u, false);
    }

    @Test
    public void checkStudyResult_andList_ok_andLockedCheck() throws Exception {
        Checker checker = new Checker();
        Study s = newStudy(1L);
        User u = newUser("member");
        s.addUser(u);
        StudyResult sr = newStudyResult(s);

        s.setLocked(false);
        checker.checkStudyResult(sr, u, true);
        checker.checkStudyResults(Collections.singletonList(sr), u, true);

        s.setLocked(true);
        boolean threw = false;
        try {
            checker.checkStudyResult(sr, u, true);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
        checker.checkStudyResult(sr, u, false);
    }

    @Test
    public void checkWorker_nullThrows_elseOk() throws Exception {
        Checker checker = new Checker();
        boolean thrown = false;
        try {
            checker.checkWorker(null, 1L);
        } catch (BadRequestException e) {
            thrown = true;
        }
        assertThat(thrown).isTrue();

        checker.checkWorker(newWorker(3L), 3L); // should not throw
    }

    @Test
    public void isUserAllowedToAccessWorker_containsWorker_ok_elseForbidden() throws Exception {
        Checker checker = new Checker();
        // Build: user -> study -> batch -> worker
        User u = newUser("u");
        Study s = newStudy(1L);
        Batch b = newBatch(s);
        Worker w = newWorker(9L);
        b.getWorkerList().add(w);
        u.addStudy(s);

        checker.isUserAllowedToAccessWorker(u, w); // ok

        Worker w2 = newWorker(10L);
        boolean threw = false;
        try {
            checker.isUserAllowedToAccessWorker(u, w2);
        } catch (ForbiddenException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }
}
