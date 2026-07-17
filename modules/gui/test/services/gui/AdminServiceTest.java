package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import general.common.Common;
import http.common.Http.Context;
import json.common.DefaultJson;
import models.common.*;
import models.common.User.AuthMethod;
import org.junit.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.test.Helpers;
import utils.common.IOUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminService.
 */
public class AdminServiceTest {

    private static MockedStatic<Common> commonStatic;

    private UserDao userDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;
    private StudyResultDao studyResultDao;
    private IOUtils ioUtils;

    private AdminService adminService;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initCommonStatics() {
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

    @Before
    public void setup() {
        userDao = Mockito.mock(UserDao.class);
        studyDao = Mockito.mock(StudyDao.class);
        workerDao = Mockito.mock(WorkerDao.class);
        studyResultDao = Mockito.mock(StudyResultDao.class);
        ComponentResultDao componentResultDao = Mockito.mock(ComponentResultDao.class);
        ioUtils = Mockito.mock(IOUtils.class);
        DefaultJson defaultJson = new DefaultJson();
        adminService = new AdminService(userDao, studyDao, workerDao, studyResultDao, componentResultDao, ioUtils, defaultJson);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void getAllStudiesData_mapsFieldsAndCalculatesEnabledSizes() {
        Date lastStarted = Date.from(Instant.parse("2023-04-05T06:07:08Z"));
        AdminStudyMemberData member = new AdminStudyMemberData(
                1L,
                "alice",
                "Alice",
                AuthMethod.DB.name());
        AdminStudyData studyData = new AdminStudyData(
                1L,
                "study-uuid",
                "Study Title",
                true,
                "study-dir",
                2L,
                120L,
                lastStarted,
                Collections.singletonList(member));

        when(studyDao.findAllAdminStudyData(true)).thenReturn(Collections.singletonList(studyData));
        when(ioUtils.getStudyAssetsDirSize("study-dir")).thenReturn(100L);
        when(studyResultDao.findIdsByStudyId(1L)).thenReturn(Arrays.asList(11L, 12L));
        when(ioUtils.getResultUploadDirSize(11L)).thenReturn(30L);
        when(ioUtils.getResultUploadDirSize(12L)).thenReturn(50L);

        List<Map<String, Object>> studiesData = adminService.getAllStudiesData(true, true, true);

        assertThat(studiesData).hasSize(1);
        Map<String, Object> study = studiesData.get(0);
        assertThat(study.get("id")).isEqualTo(1L);
        assertThat(study.get("uuid")).isEqualTo("study-uuid");
        assertThat(study.get("title")).isEqualTo("Study Title");
        assertThat(study.get("active")).isEqualTo(true);
        assertThat(study.get("studyResultCount")).isEqualTo(2L);
        assertThat(study.get("lastStarted")).isEqualTo(lastStarted);

        @SuppressWarnings("unchecked") List<Map<String, Object>> members = (List<Map<String, Object>>) study.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("username")).isEqualTo("alice");
        assertThat(members.get(0).get("name")).isEqualTo("Alice");
        assertThat(members.get(0).get("authMethod")).isEqualTo(AuthMethod.DB.name());

        @SuppressWarnings("unchecked") Map<String, Object> studyAssetsSize = (Map<String, Object>) study.get("studyAssetsSize");
        assertThat(studyAssetsSize.get("size")).isEqualTo(100L);

        @SuppressWarnings("unchecked") Map<String, Object> resultDataSize = (Map<String, Object>) study.get("resultDataSize");
        assertThat(resultDataSize.get("size")).isEqualTo(120L);
        assertThat(resultDataSize.get("averagePerResult")).isEqualTo(60L);

        @SuppressWarnings("unchecked") Map<String, Object> resultFileSize = (Map<String, Object>) study.get("resultFileSize");
        assertThat(resultFileSize.get("size")).isEqualTo(80L);
        assertThat(resultFileSize.get("averagePerResult")).isEqualTo(40L);

        Mockito.verify(studyDao).findAllAdminStudyData(true);
    }

    @Test
    public void getStudiesDataByUser_usesUsernameAndReturnsDisabledSizes() {
        Date lastStarted = Date.from(Instant.parse("2023-05-06T07:08:09Z"));
        AdminStudyMemberData member = new AdminStudyMemberData(
                2L,
                "bob",
                "Bob",
                User.AuthMethod.LDAP.name());
        AdminStudyData studyData = new AdminStudyData(
                2L,
                "user-study-uuid",
                "User Study",
                false,
                "user-study-dir",
                0L,
                null,
                lastStarted,
                Collections.singletonList(member));

        when(studyDao.findAdminStudyDataByUsername("bob", false)).thenReturn(Collections.singletonList(studyData));

        List<Map<String, Object>> studiesData = adminService.getStudiesDataByUser("bob", false, false, false);

        assertThat(studiesData).hasSize(1);
        Map<String, Object> study = studiesData.get(0);
        assertThat(study.get("id")).isEqualTo(2L);
        assertThat(study.get("uuid")).isEqualTo("user-study-uuid");
        assertThat(study.get("title")).isEqualTo("User Study");
        assertThat(study.get("active")).isEqualTo(false);
        assertThat(study.get("studyResultCount")).isEqualTo(0L);
        assertThat(study.get("lastStarted")).isEqualTo(lastStarted);

        @SuppressWarnings("unchecked") List<Map<String, Object>> members = (List<Map<String, Object>>) study.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("username")).isEqualTo("bob");
        assertThat(members.get(0).get("name")).isEqualTo("Bob");
        assertThat(members.get(0).get("authMethod")).isEqualTo(User.AuthMethod.LDAP.name());

        assertDisabledSize(study.get("studyAssetsSize"));
        assertDisabledSize(study.get("resultDataSize"));
        assertDisabledSize(study.get("resultFileSize"));

        Mockito.verify(studyDao).findAdminStudyDataByUsername("bob", false);
        Mockito.verify(ioUtils, Mockito.never()).getStudyAssetsDirSize(Mockito.anyString());
        Mockito.verify(studyResultDao, Mockito.never()).findIdsByStudyId(Mockito.anyLong());
    }

    @SuppressWarnings("unchecked")
    private void assertDisabledSize(Object sizeData) {
        Map<String, Object> size = (Map<String, Object>) sizeData;
        assertThat(size.get("humanReadable")).isEqualTo("disabled");
        assertThat(size.get("size")).isEqualTo(0);
    }

    @Test
    public void getLatestUsers_filtersAndFormats() {
        User signedIn = new User("alice", "Alice", "alice@example.org");
        Context.current().args().put(SIGNEDIN_USER, signedIn);

        // Users returned by DAO
        User u1 = new User("bob", "Bob", "bob@example.org");
        u1.setAuthMethod(AuthMethod.DB);
        u1.setLastSeen(Timestamp.from(Instant.parse("2021-01-01T00:00:00Z")));
        // should be filtered out because of null lastSeen
        User u2 = new User("carol", "Carol", "carol@example.org");
        u2.setAuthMethod(AuthMethod.LDAP);
        u2.setLastSeen(null);
        // same as signed-in -> filtered out
        User u3 = new User("alice", "Alice", "alice@example.org");
        u3.setAuthMethod(AuthMethod.DB);
        u3.setLastSeen(Timestamp.from(Instant.parse("2021-02-01T00:00:00Z")));
        when(userDao.findLastSeen(anyInt())).thenReturn(Arrays.asList(u1, u2, u3));

        List<Map<String, String>> latest = adminService.getLatestUsers(10);

        assertThat(latest).hasSize(1);
        Map<String, String> u = latest.get(0);
        assertThat(u.get("username")).isEqualTo("bob");
        assertThat(u.get("name")).isEqualTo("Bob");
        assertThat(u.get("authMethod")).isEqualTo(AuthMethod.DB.name());
        assertThat(u.get("time")).isEqualTo("2021-01-01T00:00:00Z");
    }

    @Test
    public void getLatestStudyRuns_mapsFields() {
        Study s = new Study();
        s.setTitle("X Study");

        User u = new User("dave", "Dave", "dave@example.org");
        u.setAuthMethod(AuthMethod.DB);
        s.addUser(u);

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
        assertThat(members.get(0).get("authMethod")).isEqualTo(AuthMethod.DB.name());
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
        Context.current().args().put(SIGNEDIN_USER, new User("ignored", "ignored", "i@e"));

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
