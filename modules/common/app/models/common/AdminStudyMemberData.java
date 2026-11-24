package models.common;

/**
 * DTO used for data of study members used in the GUI administration page
 */
public record AdminStudyMemberData(Long studyId,
                                   Long userId,
                                   String username,
                                   String name,
                                   String authMethod) {}
