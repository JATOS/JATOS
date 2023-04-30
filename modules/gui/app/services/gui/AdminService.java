package services.gui;

import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import models.common.Study;
import models.common.StudyResultStatus;
import models.common.User;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class mostly for Admin controller.
 *
 * @author Kristian Lange
 */
@Singleton
public class AdminService {

    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;
    private final AuthService authenticationService;
    private final IOUtils ioUtils;

    @Inject
    AdminService(UserDao userDao, StudyDao studyDao, WorkerDao workerDao, StudyResultDao studyResultDao,
            ComponentResultDao componentResultDao, AuthService authenticationService, IOUtils ioUtils) {
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
        this.authenticationService = authenticationService;
        this.ioUtils = ioUtils;
    }

    public List<Map<String, Object>> getStudiesData(Collection<Study> studyList,
            boolean studyAssetsSizeFlag, boolean resultDataSizeFlag, boolean resultFileSizeFlag) {
        List<Map<String, Object>> studies = new ArrayList<>();
        for (Study study : studyList) {
            int studyResultCount = studyResultDao.countByStudy(study);
            Map<String, Object> studyInfo = new HashMap<>();
            studyInfo.put("id", study.getId());
            studyInfo.put("uuid", study.getUuid());
            studyInfo.put("title", study.getTitle());
            studyInfo.put("active", study.isActive());
            studyInfo.put("studyResultCount", studyResultCount);
            studyInfo.put("members", study.getUserList().stream().map(User::toString).collect(Collectors.toList()));
            if (studyAssetsSizeFlag) {
                studyInfo.put("studyAssetsSize", getStudyAssetDirSize(study));
            } else {
                studyInfo.put("studyAssetsSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }
            if (resultDataSizeFlag) {
                studyInfo.put("resultDataSize", getResultDataSize(study, studyResultCount));
            } else {
                studyInfo.put("resultDataSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }
            if (resultFileSizeFlag) {
                studyInfo.put("resultFileSize", getResultFileSize(study, studyResultCount));
            } else {
                studyInfo.put("resultFileSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }
            Optional<StudyResultStatus> srsOpt = studyResultDao.findLastStarted(study);
            if (srsOpt.isPresent()) {
                studyInfo.put("lastStarted", srsOpt.get().getStartDate());
            } else {
                studyInfo.put("lastStarted", null);
            }
            studies.add(studyInfo);
        }
        return studies;
    }

    public Map<String, Object> getStudyAssetDirSize(Study study) {
        long size = ioUtils.getStudyAssetsDirSize(study.getDirName());
        return ImmutableMap.of(
                "humanReadable", Helpers.humanReadableByteCount(size),
                "size", size);
    }

    public ImmutableMap<String, Object> getResultDataSize(Study study, int studyResultCount) {
        long size = componentResultDao.sizeByStudy(study);
        long averagePerResult = studyResultCount != 0 ? size / studyResultCount : 0;
        String resultDataSizePerStudyResultCount = (studyResultCount != 0 ?
                Helpers.humanReadableByteCount(averagePerResult) : "0 B");
        String humanReadable = Helpers.humanReadableByteCount(size)
                + " (" + resultDataSizePerStudyResultCount + ")";
        return ImmutableMap.of(
                "humanReadable", humanReadable,
                "size", size,
                "averagePerResult", averagePerResult);
    }

    public ImmutableMap<String, Object> getResultFileSize(Study study, int studyResultCount) {
        long size = studyResultDao.findIdsByStudyId(study.getId()).stream()
                .mapToLong(ioUtils::getResultUploadDirSize).sum();
        long averagePerResult = studyResultCount != 0 ? size / studyResultCount : 0;
        String resultFileSizePerStudyResultCount = studyResultCount != 0 ?
                Helpers.humanReadableByteCount(averagePerResult) : "0 B";
        String humanReadable = Helpers.humanReadableByteCount(size)
                + " (" + resultFileSizePerStudyResultCount + ")";
        return ImmutableMap.of(
                "humanReadable", humanReadable,
                "size", size,
                "averagePerResult", averagePerResult);
    }

    /**
     * Gets the last seen time of users that were active latest, except the logged in one. It is limited to 'limit'
     * latest users.
     */
    public List<Map<String, String>> getLatestUsers(int limit) {
        List<Map<String, String>> lastSeenMapOrdered = userDao.findLastSeen(limit).stream()
                .filter(u -> u.getLastSeen() != null)
                .filter(u -> !u.getUsername().equals(authenticationService.getLoggedInUser().getUsername()))
                .map(u -> ImmutableMap.of(
                        "username", u.getUsername(),
                        "time", u.getLastSeen().toInstant().toString()))
                .collect(Collectors.toList());
        return lastSeenMapOrdered;
    }

    public List<Map<String, Object>> getLatestStudyRuns(int limit) {
        return studyResultDao.findLastSeen(limit).stream()
                .map(srs -> ImmutableMap.of(
                        "studyTitle", srs.getStudy().getTitle(),
                        "time", srs.getLastSeenDate(),
                        "members",
                        srs.getStudy().getUserList().stream().map(User::toString).collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    public JsonNode getAdminStatus() {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("studyCount", studyDao.count());
        statusMap.put("studyCountTotal", studyDao.countTotal());
        statusMap.put("studyResultCount", studyResultDao.count());
        statusMap.put("studyResultCountTotal", studyResultDao.countTotal());
        statusMap.put("workerCount", workerDao.count());
        statusMap.put("workerCountTotal", workerDao.countTotal());
        statusMap.put("userCount", userDao.count());
        statusMap.put("serverTime", System.currentTimeMillis());
        statusMap.put("latestUsers", getLatestUsers(10));
        statusMap.put("latestStudyRuns", getLatestStudyRuns(10));
        return JsonUtils.asJsonNode(statusMap);
    }

}
