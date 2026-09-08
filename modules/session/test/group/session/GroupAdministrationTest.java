package group.session;

import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import group.GroupAdministration;
import group.GroupDispatcher;
import group.GroupDispatcherRegistry;
import jakarta.persistence.EntityManager;
import models.common.Batch;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import testutils.session.JPAMocker;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupAdministration.
 */
public class GroupAdministrationTest {

    private GroupDispatcherRegistry registry;
    private GroupDispatcher dispatcherCurrent;
    private GroupDispatcher dispatcherDifferent;
    private GroupResultDao groupResultDao;
    private StudyResultDao studyResultDao;

    private GroupAdministration admin;

    @Before
    public void setUp() {
        registry = mock(GroupDispatcherRegistry.class);
        dispatcherCurrent = mock(GroupDispatcher.class);
        dispatcherDifferent = mock(GroupDispatcher.class);
        groupResultDao = mock(GroupResultDao.class);
        studyResultDao = mock(StudyResultDao.class);

        admin = new GroupAdministration(registry, studyResultDao, groupResultDao);

        EntityManager entityManager = Mockito.mock(EntityManager.class);
        JPAMocker.mockDaoTransactions(entityManager, groupResultDao, studyResultDao);
    }

    private StudyResult newStudyResult(long id, Study study, Batch batch) {
        StudyResult sr = new StudyResult();
        sr.setId(id);
        sr.setStudy(study);
        sr.setBatch(batch);
        return sr;
    }

    private Study groupStudy() {
        Study s = new Study();
        s.setGroupStudy(true);
        return s;
    }

    @SuppressWarnings("SameParameterValue")
    private Batch batch(Integer maxActive, Integer maxTotal) {
        Batch b = new Batch();
        b.setMaxActiveMembers(maxActive);
        b.setMaxTotalMembers(maxTotal);
        return b;
    }

    @Test
    public void join_createsNewGroupWhenNoneExists_andSendsJoined() {
        Batch batch = batch(10, 20);
        Study study = groupStudy();
        StudyResult sr = newStudyResult(1L, study, batch);

        when(studyResultDao.findById(sr.getId())).thenReturn(sr);
        when(groupResultDao.findFirstMaxNotReachedForUpdate(batch)).thenReturn(Optional.empty());
        // Return argument back for create
        when(groupResultDao.persist(any(GroupResult.class))).thenAnswer(inv -> {
            GroupResult gr = inv.getArgument(0);
            gr.setId(10L);
            return gr;
        });

        // No dispatcher initially; sendJoinedMsg checks and is no-op if none
        when(registry.get(anyLong())).thenReturn(scala.Option.empty());

        GroupResult returned = admin.join(sr, batch);

        assertNotNull(returned);
        assertSame(returned, sr.getActiveGroupResult());
        assertTrue(returned.getActiveMemberList().contains(sr));
        verify(groupResultDao).persist(any(GroupResult.class));
        verify(groupResultDao, atLeastOnce()).merge(any(GroupResult.class));
        verify(studyResultDao).merge(sr);
        // Since no dispatcher, no joined() call
        verify(registry).get(anyLong());
        verifyNoInteractions(dispatcherCurrent);
    }

    @Test
    public void join_usesExistingGroup_andSendsJoinedIfDispatcherPresent() {
        Batch batch = batch(10, 20);
        Study study = groupStudy();
        StudyResult sr = newStudyResult(2L, study, batch);

        GroupResult existing = new GroupResult(batch);
        existing.setId(99L);

        when(studyResultDao.findById(sr.getId())).thenReturn(sr);
        when(groupResultDao.findFirstMaxNotReachedForUpdate(batch)).thenReturn(Optional.of(existing));
        when(registry.get(existing.getId())).thenReturn(scala.Option.apply(dispatcherCurrent));

        GroupResult returned = admin.join(sr, batch);

        assertSame(existing, returned);
        assertSame(existing, sr.getActiveGroupResult());
        assertTrue(existing.getActiveMemberList().contains(sr));
        // Ensure we didn't create a new one
        verify(groupResultDao, never()).persist(any(GroupResult.class));
        // Joined message should be sent
        verify(dispatcherCurrent).joined(sr.getId());
    }

    @Test
    public void leaveGroup_noop_whenNoGroupStudyOrNoActiveGroup() {
        Batch batch = batch(10, 20);
        Study studyNonGroup = new Study(); // groupStudy default false
        StudyResult sr1 = newStudyResult(3L, studyNonGroup, batch);
        // activeGroupResult null
        admin.leave(sr1);
        verifyNoInteractions(groupResultDao, studyResultDao, registry);

        // group study but no active group
        Study studyGroup = groupStudy();
        StudyResult sr2 = newStudyResult(4L, studyGroup, batch);
        admin.leave(sr2);
        verifyNoMoreInteractions(groupResultDao, studyResultDao, registry);
    }

    @Test
    public void leave_movesToHistory_closesChannel_andSendsLeft() {
        Batch batch = batch(10, 20);
        Study study = groupStudy();
        StudyResult sr = newStudyResult(5L, study, batch);

        GroupResult gr = new GroupResult(batch);
        gr.setId(100L);
        gr.addActiveMember(sr);
        sr.setActiveGroupResult(gr);

        when(studyResultDao.findById(sr.getId())).thenReturn(sr);
        when(groupResultDao.findById(gr.getId())).thenReturn(gr);
        when(registry.get(gr.getId())).thenReturn(scala.Option.apply(dispatcherCurrent));

        admin.leave(sr);

        assertNull(sr.getActiveGroupResult());
        assertSame(gr, sr.getHistoryGroupResult());
        assertEquals(0, gr.getActiveMemberCount().intValue());
        assertEquals(1, gr.getHistoryMemberCount().intValue());
        verify(groupResultDao, atLeastOnce()).merge(gr);
        verify(studyResultDao).merge(sr);
        verify(dispatcherCurrent).left(sr.getId());
        verify(dispatcherCurrent).poisonChannel(sr.getId());
    }

    @Test
    public void closeGroupChannel_onlyIfDispatcherExists() {
        // None case
        when(registry.get(222L)).thenReturn(scala.Option.empty());
        admin.closeGroupChannel(111L, 222L);
        verify(registry).get(222L);
        verifyNoInteractions(dispatcherCurrent);

        // Some case
        when(registry.get(222L)).thenReturn(scala.Option.apply(dispatcherCurrent));
        admin.closeGroupChannel(111L, 222L);
        verify(dispatcherCurrent).poisonChannel(111L);
    }

    @Test
    public void reassign_success_movesMembershipAndChannel() {
        Batch batch = batch(10, 20);
        Study study = groupStudy();
        StudyResult sr = newStudyResult(6L, study, batch);

        GroupResult current = new GroupResult(batch);
        current.setId(200L);
        current.addActiveMember(sr);
        sr.setActiveGroupResult(current);

        GroupResult different = new GroupResult(batch);
        different.setId(201L);

        // findFirstDifferentMaxNotReachedForUpdate returns group "different"
        when(groupResultDao.findFirstDifferentMaxNotReachedForUpdate(batch, current)).thenReturn(Optional.of(different));
        when(registry.get(current.getId())).thenReturn(scala.Option.apply(dispatcherCurrent));
        when(studyResultDao.findById(sr.getId())).thenReturn(sr);
        when(groupResultDao.findById(current.getId())).thenReturn(current);
        when(registry.getOrRegister(different.getId())).thenReturn(dispatcherDifferent);

        boolean reassigned = admin.reassign(sr, batch);
        assertTrue(reassigned);
        assertSame(different, sr.getActiveGroupResult());
        assertFalse(current.getActiveMemberList().contains(sr));
        assertTrue(different.getActiveMemberList().contains(sr));

        // DAO updates performed
        verify(groupResultDao).merge(current);
        verify(groupResultDao).merge(different);
        verify(studyResultDao).merge(sr);
        // Channel reassigned
        verify(dispatcherCurrent).reassignChannel(sr.getId(), dispatcherDifferent);
    }

    @Test
    public void reassign_noOtherGroup_returnsFalse() {
        Batch batch = batch(10, 20);
        Study study = groupStudy();
        StudyResult sr = newStudyResult(7L, study, batch);

        GroupResult current = new GroupResult(batch);
        current.setId(300L);
        current.addActiveMember(sr);
        sr.setActiveGroupResult(current);

        // Only current available -> empty Optional
        when(groupResultDao.findFirstDifferentMaxNotReachedForUpdate(batch, current)).thenReturn(Optional.empty());

        boolean reassigned = admin.reassign(sr, batch);
        assertFalse(reassigned);
        assertSame(current, sr.getActiveGroupResult());
        verify(registry, never()).get(anyLong());
        verify(registry, never()).getOrRegister(anyLong());
    }

    @Test
    public void leave_finishesGroup_whenHistoryReachesMaxTotal_andClearsSessionData() {
        Batch batch = batch(10, 1); // maxTotalMembers = 1
        Study study = groupStudy();
        StudyResult sr = newStudyResult(8L, study, batch);

        GroupResult gr = new GroupResult(batch);
        gr.setId(400L);
        gr.addActiveMember(sr);
        sr.setActiveGroupResult(gr);

        // After leaving, active=0 and history=1 which equals maxTotal -> finish
        when(registry.get(gr.getId())).thenReturn(scala.Option.empty());
        when(studyResultDao.findById(sr.getId())).thenReturn(sr);
        when(groupResultDao.findById(gr.getId())).thenReturn(gr);
        admin.leave(sr);

        assertEquals(GroupResult.GroupState.FINISHED, gr.getGroupState());
        assertNull("groupSessionData should be cleared on finish", gr.getGroupSessionData());
        assertNotNull("endDate should be set", gr.getEndDate());
        verify(groupResultDao, atLeastOnce()).merge(gr);
    }
}
