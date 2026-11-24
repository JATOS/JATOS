package models.common;

import java.util.Date;
import java.util.List;

/**
 * DTO used for study data used in the GUI administration page
 */
public record AdminStudyData(Long id,
                             String uuid,
                             String title,
                             boolean active,
                             String dirName,
                             long studyResultCount,
                             Long resultDataSize,
                             Date lastStarted,
                             List<AdminStudyMemberData> members,
                             String ownerUsername) {}
