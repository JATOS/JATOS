package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import http.common.Http.Context;
import json.common.DefaultJson;
import models.common.AdminStudyData;
import models.common.Study;
import models.common.User;
import utils.common.IOUtils;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class for everything related to Admin.
 */
@Singleton
public class AdminService {

    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;
    private final IOUtils ioUtils;
    private final DefaultJson defaultJson;

    @Inject
    AdminService(UserDao userDao,
                 StudyDao studyDao,
                 WorkerDao workerDao,
                 StudyResultDao studyResultDao,
                 ComponentResultDao componentResultDao,
                 IOUtils ioUtils,
                 DefaultJson defaultJson) {
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
        this.ioUtils = ioUtils;
        this.defaultJson = defaultJson;
    }

    public List<Map<String, Object>> getAllStudiesData(
            boolean studyAssetsSizeFlag,
            boolean resultDataSizeFlag,
            boolean resultFileSizeFlag) {
        return getStudiesData(
                studyDao.findAllAdminStudyData(resultDataSizeFlag),
                studyAssetsSizeFlag,
                resultDataSizeFlag,
                resultFileSizeFlag);
    }

    public List<Map<String, Object>> getStudiesDataByUser(
            String username,
            boolean studyAssetsSizeFlag,
            boolean resultDataSizeFlag,
            boolean resultFileSizeFlag) {
        return getStudiesData(
                studyDao.findAdminStudyDataByUsername(username, resultDataSizeFlag),
                studyAssetsSizeFlag,
                resultDataSizeFlag,
                resultFileSizeFlag);
    }

    private List<Map<String, Object>> getStudiesData(
            Collection<AdminStudyData> studyDataList,
            boolean studyAssetsSizeFlag,
            boolean resultDataSizeFlag,
            boolean resultFileSizeFlag) {
        return studyDataList.stream().map(studyData -> {
            long studyResultCount = studyData.getStudyResultCount();

            Map<String, Object> studyInfo = new HashMap<>();
            studyInfo.put("id", studyData.getId());
            studyInfo.put("uuid", studyData.getUuid());
            studyInfo.put("title", studyData.getTitle());
            studyInfo.put("active", studyData.isActive());
            studyInfo.put("studyResultCount", studyResultCount);
            studyInfo.put("members", studyData.getMembers().stream().map(u -> ImmutableMap.of(
                    "username", u.getUsername(),
                    "name", u.getName(),
                    "authMethod", u.getAuthMethod()
            )).collect(Collectors.toList()));

            if (studyAssetsSizeFlag) {
                studyInfo.put("studyAssetsSize", getStudyAssetDirSize(studyData.getDirName()));
            } else {
                studyInfo.put("studyAssetsSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }
            if (resultDataSizeFlag) {
                studyInfo.put("resultDataSize", getResultDataSize(studyData.getResultDataSize(), studyResultCount));
            } else {
                studyInfo.put("resultDataSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }
            if (resultFileSizeFlag) {
                studyInfo.put("resultFileSize", getResultFileSize(studyData.getId(), studyResultCount));
            } else {
                studyInfo.put("resultFileSize", ImmutableMap.of("humanReadable", "disabled", "size", 0));
            }

            studyInfo.put("lastStarted", studyData.getLastStarted());
            return studyInfo;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getStudyAssetDirSize(Study study) {
        return getStudyAssetDirSize(study.getDirName());
    }

    public Map<String, Object> getStudyAssetDirSize(String dirName) {
        long size = ioUtils.getStudyAssetsDirSize(dirName);
        return ImmutableMap.of(
                "humanReadable", StringUtils.humanReadableByteCount(size),
                "size", size);
    }

    public ImmutableMap<String, Object> getResultDataSize(Study study, int studyResultCount) {
        long size = componentResultDao.sizeByStudy(study);
        return getResultDataSize(size, studyResultCount);
    }

    public ImmutableMap<String, Object> getResultDataSize(long size, long studyResultCount) {
        long averagePerResult = studyResultCount != 0 ? size / studyResultCount : 0;
        String resultDataSizePerStudyResultCount = (studyResultCount != 0
                ? StringUtils.humanReadableByteCount(averagePerResult)
                : "0 B");
        String humanReadable = StringUtils.humanReadableByteCount(size) + " (" + resultDataSizePerStudyResultCount + ")";
        return ImmutableMap.of(
                "humanReadable", humanReadable,
                "size", size,
                "averagePerResult", averagePerResult);
    }

    public ImmutableMap<String, Object> getResultFileSize(Study study, int studyResultCount) {
        return getResultFileSize(study.getId(), studyResultCount);
    }

    public ImmutableMap<String, Object> getResultFileSize(Long studyId, long studyResultCount) {
        long size = studyResultDao.findIdsByStudyId(studyId)
                .stream()
                .mapToLong(ioUtils::getResultUploadDirSize)
                .sum();
        long averagePerResult = studyResultCount != 0 ? size / studyResultCount : 0;
        String resultFileSizePerStudyResultCount = studyResultCount != 0
                ? StringUtils.humanReadableByteCount(averagePerResult)
                : "0 B";
        String humanReadable = StringUtils.humanReadableByteCount(size) + " (" + resultFileSizePerStudyResultCount + ")";
        return ImmutableMap.of(
                "humanReadable", humanReadable,
                "size", size,
                "averagePerResult", averagePerResult);
    }

    /**
     * Gets the last seen time of users that were active latest, except the signed-in one. It is limited to 'limit'
     * latest users.
     */
    public List<Map<String, String>> getLatestUsers(int limit) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return userDao.findLastSeen(limit).stream()
                .filter(u -> u.getLastSeen() != null)
                .filter(u -> !u.getUsername().equals(signedinUser.getUsername()))
                .map(u -> ImmutableMap.of(
                        "username", u.getUsername(),
                        "name", u.getName(),
                        "authMethod", u.getAuthMethod().name(),
                        "time", u.getLastSeen().toInstant().toString()))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLatestStudyRuns(int limit) {
        return studyResultDao.withReadOnlyTransaction(em -> {
            return studyResultDao.findLastSeen(limit).stream()
                    .map(srs -> ImmutableMap.of(
                            "studyTitle", srs.getStudy().getTitle(),
                            "time", srs.getLastSeenDate(),
                            "members", srs.getStudy().getUserList().stream().map(u -> ImmutableMap.of(
                                    "username", u.getUsername(),
                                    "name", u.getName(),
                                    "authMethod", u.getAuthMethod().name()
                            )).collect(Collectors.toList())))
                    .collect(Collectors.toList());
        });
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
        return defaultJson.objAsJsonNode(statusMap);
    }

}
