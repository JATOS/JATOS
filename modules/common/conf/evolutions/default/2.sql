Add lastSeenDate for heart beat in v2.2.5 

# --- !Ups
ALTER TABLE `StudyResult` ADD `lastSeenDate` datetime DEFAULT NULL;

# --- !Downs
ALTER TABLE `StudyResult` DROP `lastSeenDate`;
