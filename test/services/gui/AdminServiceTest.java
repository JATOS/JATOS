package services.gui;

import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import models.common.Study;
import models.common.StudyResultStatus;
import models.common.User;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import utils.common.IOUtils;
import general.common.Common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminService.
 */
public class AdminServiceTest {

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    public static void initCommonStatics() {
        // Mock Common's static getters used by IOUtils and others
        String tmp = System.getProperty("java.io.tmpdir") + java.io.File.separator + "jatos-test";
        commonStatic = Mockito.mockStatic(Common.class);
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonStatic.when(Common::getResultUploadsPath).thenReturn(tmp);
    }

    @AfterClass
    public static void tearDownCommonStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    private UserDao userDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;
    private StudyResultDao studyResultDao;
    private ComponentResultDao componentResultDao;
    private AuthService authService;
    private IOUtils ioUtils;

    private AdminService adminService;

    private Study study;

    @Before
    public void setup() {
        userDao = Mockito.mock(UserDao.class);
        studyDao = Mockito.mock(StudyDao.class);
        workerDao = Mockito.mock(WorkerDao.class);
        studyResultDao = Mockito.mock(StudyResultDao.class);
        componentResultDao = Mockito.mock(ComponentResultDao.class);
        authService = Mockito.mock(AuthService.class);
        ioUtils = Mockito.mock(IOUtils.class);
        adminService = new AdminService(userDao, studyDao, workerDao, studyResultDao, componentResultDao, authService, ioUtils);

        study = new Study();
        study.setId(1L);
        study.setUuid("uuid-1");
        study.setTitle("Test Study");
        study.setActive(true);
        study.setDirName("dir-1");

        // One member
        User member = new User("member", "Member Name", "member@example.org");
        member.setAuthMethod(User.AuthMethod.DB);
        study.setUserList(new HashSet<>(Collections.singletonList(member)));
    }

    @Test
    public void getStudiesData_allFlagsTrue_andLastStartedPresent() {
        // Given
        when(studyResultDao.countByStudy(study)).thenReturn(4);
        when(ioUtils.getStudyAssetsDirSize("dir-1")).thenReturn(2_000L); // 2.0 kB
        when(componentResultDao.sizeByStudy(study)).thenReturn(10_000L); // 10.0 kB total
        when(studyResultDao.findIdsByStudyId(1L)).thenReturn(Arrays.asList(11L, 12L, 13L, 14L));
        when(ioUtils.getResultUploadDirSize(anyLong())).thenReturn(3_000L); // each 3.0 kB => total 12.0 kB
        StudyResultStatus srs = new StudyResultStatus();
        srs.setStartDate(Timestamp.from(Instant.parse("2020-01-02T03:04:05Z")));
        when(studyResultDao.findLastStarted(study)).thenReturn(Optional.of(srs));

        // When
        List<Map<String, Object>> studiesData = adminService.getStudiesData(Collections.singletonList(study), true, true, true);

        // Then
        assertThat(studiesData).hasSize(1);
        Map<String, Object> studyInfo = studiesData.get(0);
        assertThat(studyInfo.get("id")).isEqualTo(1L);
        assertThat(studyInfo.get("uuid")).isEqualTo("uuid-1");
        assertThat(studyInfo.get("title")).isEqualTo("Test Study");
        assertThat(studyInfo.get("active")).isEqualTo(true);
        assertThat(studyInfo.get("studyResultCount")).isEqualTo(4);
        // studyAssetsSize
        @SuppressWarnings("unchecked") Map<String, Object> assets = (Map<String, Object>) studyInfo.get("studyAssetsSize");
        assertThat(assets.get("size")).isEqualTo(2000L);
        assertThat(String.valueOf(assets.get("humanReadable"))).isEqualTo("2.0 kB");
        // resultDataSize total and average
        @SuppressWarnings("unchecked") Map<String, Object> rdata = (Map<String, Object>) studyInfo.get("resultDataSize");
        assertThat(rdata.get("size")).isEqualTo(10000L);
        assertThat(rdata.get("averagePerResult")).isEqualTo(2500L); // 10k / 4
        assertThat(String.valueOf(rdata.get("humanReadable"))).isEqualTo("10.0 kB (2.5 kB)");
        // resultFileSize total and average
        @SuppressWarnings("unchecked") Map<String, Object> rfiles = (Map<String, Object>) studyInfo.get("resultFileSize");
        assertThat(rfiles.get("size")).isEqualTo(12_000L);
        assertThat(rfiles.get("averagePerResult")).isEqualTo(3_000L);
        assertThat(String.valueOf(rfiles.get("humanReadable"))).isEqualTo("12.0 kB (3.0 kB)");
        // lastStarted
        assertThat(studyInfo.get("lastStarted")).isEqualTo(Timestamp.from(Instant.parse("2020-01-02T03:04:05Z")));
        // members
        @SuppressWarnings("unchecked") List<Map<String, Object>> members = (List<Map<String, Object>>) studyInfo.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("username")).isEqualTo("member");
        assertThat(members.get(0).get("name")).isEqualTo("Member Name");
        assertThat(members.get(0).get("authMethod")).isEqualTo(User.AuthMethod.DB.name());
    }

    @Test
    public void getStudiesData_flagsFalse_putsDisabledStrings() {
        when(studyResultDao.countByStudy(study)).thenReturn(0);
        when(studyResultDao.findLastStarted(study)).thenReturn(Optional.empty());

        List<Map<String, Object>> studiesData = adminService.getStudiesData(Collections.singletonList(study), false, false, false);

        assertThat(studiesData).hasSize(1);
        Map<String, Object> studyInfo = studiesData.get(0);
        @SuppressWarnings("unchecked") Map<String, Object> studyAssetsSize = (Map<String, Object>) studyInfo.get("studyAssetsSize");
        assertThat(studyAssetsSize.get("humanReadable")).isEqualTo("disabled");
        assertThat(studyAssetsSize.get("size")).isEqualTo(0);
        @SuppressWarnings("unchecked") Map<String, Object> resultDataSize = (Map<String, Object>) studyInfo.get("resultDataSize");
        assertThat(resultDataSize.get("humanReadable")).isEqualTo("disabled");
        assertThat(resultDataSize.get("size")).isEqualTo(0);
        @SuppressWarnings("unchecked") Map<String, Object> resultFileSize = (Map<String, Object>) studyInfo.get("resultFileSize");
        assertThat(resultFileSize.get("humanReadable")).isEqualTo("disabled");
        assertThat(resultFileSize.get("size")).isEqualTo(0);
        assertThat(studyInfo.get("lastStarted")).isNull();
    }

    @Test
    public void getLatestUsers_filtersAndFormats() {
        // Signed-in user
        User signedIn = new User("alice", "Alice", "alice@example.org");
        when(authService.getSignedinUser()).thenReturn(signedIn);

        // Users returned by DAO
        User u1 = new User("bob", "Bob", "bob@example.org");
        u1.setAuthMethod(User.AuthMethod.DB);
        u1.setLastSeen(Timestamp.from(Instant.parse("2021-01-01T00:00:00Z")));
        // should be filtered out because of null lastSeen
        User u2 = new User("carol", "Carol", "carol@example.org");
        u2.setAuthMethod(User.AuthMethod.LDAP);
        u2.setLastSeen(null);
        // same as signed-in -> filtered out
        User u3 = new User("alice", "Alice", "alice@example.org");
        u3.setAuthMethod(User.AuthMethod.DB);
        u3.setLastSeen(Timestamp.from(Instant.parse("2021-02-01T00:00:00Z")));

        when(userDao.findLastSeen(anyInt())).thenReturn(Arrays.asList(u1, u2, u3));

        List<Map<String, String>> latest = adminService.getLatestUsers(10);

        assertThat(latest).hasSize(1);
        Map<String, String> u = latest.get(0);
        assertThat(u.get("username")).isEqualTo("bob");
        assertThat(u.get("name")).isEqualTo("Bob");
        assertThat(u.get("authMethod")).isEqualTo(User.AuthMethod.DB.name());
        assertThat(u.get("time")).isEqualTo("2021-01-01T00:00:00Z");
    }

    @Test
    public void getLatestStudyRuns_mapsFields() {
        Study s = new Study();
        s.setTitle("X Study");

        User u = new User("dave", "Dave", "dave@example.org");
        u.setAuthMethod(User.AuthMethod.DB);
        s.setUserList(new HashSet<>(Collections.singletonList(u)));

        StudyResultStatus srs = new StudyResultStatus();
        srs.setStudy(s);
        Timestamp ts = Timestamp.from(Instant.parse("2022-03-04T05:06:07Z"));
        srs.setLastSeenDate(ts);

        when(studyResultDao.findLastSeen(5)).thenReturn(Collections.singletonList(srs));

        List<Map<String, Object>> res = adminService.getLatestStudyRuns(5);
        assertThat(res).hasSize(1);
        Map<String, Object> m = res.get(0);
        assertThat(m.get("studyTitle")).isEqualTo("X Study");
        assertThat(m.get("time")).isEqualTo(ts);
        @SuppressWarnings("unchecked") List<Map<String, Object>> members = (List<Map<String, Object>>) m.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("username")).isEqualTo("dave");
        assertThat(members.get(0).get("authMethod")).isEqualTo(User.AuthMethod.DB.name());
    }

    @Test
    public void getAdminStatus_aggregatesCountsAndLists() {
        when(studyDao.count()).thenReturn(3);
        when(studyDao.countTotal()).thenReturn(5);
        when(studyResultDao.count()).thenReturn(7);
        when(studyResultDao.countTotal()).thenReturn(11);
        when(workerDao.count()).thenReturn(13);
        when(workerDao.countTotal()).thenReturn(17);
        when(userDao.count()).thenReturn(19);

        // latest lists
        when(userDao.findLastSeen(anyInt())).thenReturn(Collections.emptyList());
        when(studyResultDao.findLastSeen(anyInt())).thenReturn(Collections.emptyList());
        when(authService.getSignedinUser()).thenReturn(new User("ignored", "ignored", "i@e"));

        JsonNode json = adminService.getAdminStatus();
        assertThat(json.get("studyCount").asInt()).isEqualTo(3);
        assertThat(json.get("studyCountTotal").asInt()).isEqualTo(5);
        assertThat(json.get("studyResultCount").asInt()).isEqualTo(7);
        assertThat(json.get("studyResultCountTotal").asInt()).isEqualTo(11);
        assertThat(json.get("workerCount").asInt()).isEqualTo(13);
        assertThat(json.get("workerCountTotal").asInt()).isEqualTo(17);
        assertThat(json.get("userCount").asInt()).isEqualTo(19);
        assertThat(json.get("serverTime").asLong()).isGreaterThan(0L);
        assertThat(json.get("latestUsers").isArray()).isTrue();
        assertThat(json.get("latestStudyRuns").isArray()).isTrue();
    }
}
