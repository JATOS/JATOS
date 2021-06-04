package services.gui;

import com.google.common.collect.ImmutableMap;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import models.common.Study;
import models.common.StudyResultStatus;
import models.common.User;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
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
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;
    private final AuthenticationService authenticationService;
    private final IOUtils ioUtils;
    private final UserSessionCacheAccessor userSessionCacheAccessor;

    @Inject
    AdminService(UserDao userDao, StudyResultDao studyResultDao, ComponentResultDao componentResultDao,
            AuthenticationService authenticationService, IOUtils ioUtils,
            UserSessionCacheAccessor userSessionCacheAccessor) {
        this.userDao = userDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
        this.authenticationService = authenticationService;
        this.ioUtils = ioUtils;
        this.userSessionCacheAccessor = userSessionCacheAccessor;
    }

    public List<Map<String, Object>> getStudiesData(Collection<Study> studyList) {
        List<Map<String, Object>> studies = new ArrayList<>();
        for (Study study : studyList) {
            Map<String, Object> studyInfo = new HashMap<>();
            studyInfo.put("id", study.getId());
            studyInfo.put("uuid", study.getUuid());
            studyInfo.put("title", study.getTitle());
            studyInfo.put("active", study.isActive());
            studyInfo.put("studyResultCount", studyResultDao.countByStudy(study));
            studyInfo.put("members",
                    study.getUserList().stream().map(User::toString).collect(Collectors.toList()));
            long studyAssetsDirSize = ioUtils.getStudyAssetsDirSize(study.getDirName());
            studyInfo.put("studyAssetsSize", ImmutableMap.of(
                    "display", Helpers.humanReadableByteCountSI(studyAssetsDirSize),
                    "bytes", studyAssetsDirSize));
            long resultDataSize = componentResultDao.sizeByStudy(study);
            studyInfo.put("resultDataSize", ImmutableMap.of(
                    "display", Helpers.humanReadableByteCountSI(resultDataSize),
                    "bytes", resultDataSize));
            long resultFileSize = studyResultDao.findAllByStudy(
                    study).stream().mapToLong(sr -> ioUtils.getResultUploadDirSize(sr.getId())).sum();
            studyInfo.put("resultFileSize", ImmutableMap.of(
                    "display", Helpers.humanReadableByteCountSI(resultFileSize),
                    "bytes", resultFileSize));
            Optional<StudyResultStatus> srsOpt = studyResultDao.findLastStarted(study);
            if (srsOpt.isPresent()) {
                studyInfo.put("lastStarted", Helpers.formatDate(srsOpt.get().getStartDate()));
            } else {
                studyInfo.put("lastStarted", "never");
            }
            studies.add(studyInfo);
        }
        return studies;
    }

    /**
     * Gets the last seen time of users that were active latest, except the logged in one. It is limited to 'limit'
     * latest users. If non of the users exist in the cache (never logged in) it returns Instant.EPOCH.
     */
    public List<Map<String, String>> getLatestUsers(int limit) {
        List<String> normalizedUsernameList = userDao.findAll().stream()
                .map(User::getUsername)
                .filter(u -> !u.equals(authenticationService.getLoggedInUser().getUsername()))
                .collect(Collectors.toList());

        Map<String, Instant> lastSeenMap = new HashMap<>();
        for (String normalizedUsername : normalizedUsernameList) {
            Instant lastSeen = userSessionCacheAccessor.getLastSeen(normalizedUsername);
            lastSeenMap.put(normalizedUsername, lastSeen);
        }

        List<Map<String, String>> lastSeenMapOrdered = lastSeenMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> ImmutableMap.of(
                        "username", e.getKey(),
                        "time", Helpers.formatDate(Date.from(e.getValue()))))
                .collect(Collectors.toList());
        return lastSeenMapOrdered;
    }

    public List<Map<String, Object>> getLatestStudyRuns(int limit) {
        return studyResultDao.findLastSeen(limit).stream()
                .map(srs -> ImmutableMap.of(
                        "studyTitle", srs.getStudy().getTitle(),
                        "time", Helpers.formatDate(srs.getLastSeenDate()),
                        "members", srs.getStudy().getUserList().stream().map(User::toString).collect(Collectors.toList())))
                .collect(Collectors.toList());

    }

}
