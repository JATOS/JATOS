package services.gui;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
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
