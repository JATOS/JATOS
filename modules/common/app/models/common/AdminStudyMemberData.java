package models.common;

/**
 * DTO used for data of study members used in the GUI administration page
 */
public class AdminStudyMemberData {

    private final Long studyId;
    private final String username;
    private final String name;
    private final String authMethod;

    public AdminStudyMemberData(Long studyId, String username, String name, String authMethod) {
        this.studyId = studyId;
        this.username = username;
        this.name = name;
        this.authMethod = authMethod;
    }

    public Long getStudyId() {
        return studyId;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getAuthMethod() {
        return authMethod;
    }
}
