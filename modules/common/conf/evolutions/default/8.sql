# --- Add counts for active member and history member in GroupResult table

# --- !Ups
ALTER TABLE `GroupResult` ADD `activeMemberCount` int(11);
UPDATE GroupResult SET activeMemberCount = ( SELECT COUNT(*) FROM StudyResult WHERE activeGroupMember_id = GroupResult.id );

ALTER TABLE `GroupResult` ADD `historyMemberCount` int(11);
UPDATE GroupResult SET historyMemberCount = ( SELECT COUNT(*) FROM StudyResult WHERE historyGroupMember_id = GroupResult.id );

# --- !Downs
# --- not supported
