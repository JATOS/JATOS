package models.common;

import java.util.Date;
import java.util.List;

/**
 * DTO used for study data used in the GUI administration page
 */
public class AdminStudyData {

    private final Long id;
    private final String uuid;
    private final String title;
    private final boolean active;
    private final String dirName;
    private final long studyResultCount;
    private final Long resultDataSize;
    private final Date lastStarted;
    private final List<AdminStudyMemberData> members;

    public AdminStudyData(Long id, String uuid, String title, boolean active, String dirName, long studyResultCount, Long resultDataSize, Date lastStarted, List<AdminStudyMemberData> members) {
        this.id = id;
        this.uuid = uuid;
        this.title = title;
        this.active = active;
        this.dirName = dirName;
        this.studyResultCount = studyResultCount;
        this.resultDataSize = resultDataSize;
        this.lastStarted = lastStarted;
        this.members = members;
    }

    public Long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public boolean isActive() {
        return active;
    }

    public String getDirName() {
        return dirName;
    }

    public long getStudyResultCount() {
        return studyResultCount;
    }

    public Long getResultDataSize() {
        return resultDataSize;
    }

    public Date getLastStarted() {
        return lastStarted;
    }

    public List<AdminStudyMemberData> getMembers() {
        return members;
    }
}
