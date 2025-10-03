package services.gui;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import models.gui.GroupSession;
import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupService.
 */
public class GroupServiceTest {

    private GroupResultDao groupResultDao;
    private GroupService groupService;

    @Before
    public void setUp() {
        groupResultDao = mock(GroupResultDao.class);
        groupService = new GroupService(groupResultDao);
    }

    private GroupResult newGroupResult(long id, long version, String data, GroupState state) {
        GroupResult gr = new GroupResult();
        gr.setId(id);
        gr.setGroupSessionVersion(version);
        gr.setGroupSessionData(data);
        gr.setGroupState(state);
        return gr;
    }

    @Test
    public void bindToGroupSession_copiesVersionAndData() {
        GroupResult gr = newGroupResult(1L, 5L, "{\"a\":1}", GroupState.STARTED);

        GroupSession session = groupService.bindToGroupSession(gr);

        assertThat(session.getVersion()).isEqualTo(5L);
        assertThat(session.getData()).isEqualTo("{\"a\":1}");
    }

    @Test
    public void updateGroupSession_returnsFalse_ifGroupResultNotFound() {
        when(groupResultDao.findById(123L)).thenReturn(null);

        GroupSession session = new GroupSession();
        session.setVersion(1L);
        session.setData("{}");

        boolean result = groupService.updateGroupSession(123L, session);

        assertThat(result).isFalse();
        verify(groupResultDao, never()).update(any());
    }

    @Test
    public void updateGroupSession_returnsFalse_ifVersionMismatch() {
        GroupResult current = newGroupResult(1L, 2L, "{}", GroupState.STARTED);
        when(groupResultDao.findById(1L)).thenReturn(current);

        GroupSession session = new GroupSession();
        session.setVersion(1L); // mismatch
        session.setData("{\"x\":1}");

        boolean result = groupService.updateGroupSession(1L, session);

        assertThat(result).isFalse();
        verify(groupResultDao, never()).update(any());
    }

    @Test
    public void updateGroupSession_setsEmptyObjectWhenNullOrEmpty_andIncrementsVersion_andPersists() {
        GroupResult current = newGroupResult(1L, 3L, "{\"old\":true}", GroupState.STARTED);
        when(groupResultDao.findById(1L)).thenReturn(current);

        GroupSession session = new GroupSession();
        session.setVersion(3L); // match
        session.setData(""); // empty should become {}

        boolean result = groupService.updateGroupSession(1L, session);

        assertThat(result).isTrue();
        assertThat(current.getGroupSessionVersion()).isEqualTo(4L);
        assertThat(current.getGroupSessionData()).isEqualTo("{}");
        verify(groupResultDao, times(1)).update(current);
    }

    @Test
    public void updateGroupSession_keepsNonEmptyData_andIncrementsVersion_andPersists() {
        GroupResult current = newGroupResult(1L, 7L, "{\"old\":true}", GroupState.STARTED);
        when(groupResultDao.findById(1L)).thenReturn(current);

        GroupSession session = new GroupSession();
        session.setVersion(7L); // match
        session.setData("{\"new\":123}");

        boolean result = groupService.updateGroupSession(1L, session);

        assertThat(result).isTrue();
        assertThat(current.getGroupSessionVersion()).isEqualTo(8L);
        assertThat(current.getGroupSessionData()).isEqualTo("{\"new\":123}");
        verify(groupResultDao, times(1)).update(current);
    }

    @Test
    public void toggleGroupFixed_setsFixedWhenStarted_andFixedTrue() {
        GroupResult gr = newGroupResult(1L, 1L, "{}", GroupState.STARTED);

        GroupState state = groupService.toggleGroupFixed(gr, true);

        assertThat(state).isEqualTo(GroupState.FIXED);
        assertThat(gr.getGroupState()).isEqualTo(GroupState.FIXED);
        verify(groupResultDao, times(1)).update(gr);
    }

    @Test
    public void toggleGroupFixed_setsStartedWhenFixed_andFixedFalse() {
        GroupResult gr = newGroupResult(1L, 1L, "{}", GroupState.FIXED);

        GroupState state = groupService.toggleGroupFixed(gr, false);

        assertThat(state).isEqualTo(GroupState.STARTED);
        assertThat(gr.getGroupState()).isEqualTo(GroupState.STARTED);
        verify(groupResultDao, times(1)).update(gr);
    }

    @Test
    public void toggleGroupFixed_noChangeWhenAlreadyFixed_andFixedTrue() {
        GroupResult gr = newGroupResult(1L, 1L, "{}", GroupState.FIXED);

        GroupState state = groupService.toggleGroupFixed(gr, true);

        assertThat(state).isEqualTo(GroupState.FIXED);
        verify(groupResultDao, times(1)).update(gr);
    }

    @Test
    public void toggleGroupFixed_noChangeWhenAlreadyStarted_andFixedFalse() {
        GroupResult gr = newGroupResult(1L, 1L, "{}", GroupState.STARTED);

        GroupState state = groupService.toggleGroupFixed(gr, false);

        assertThat(state).isEqualTo(GroupState.STARTED);
        verify(groupResultDao, times(1)).update(gr);
    }
}
