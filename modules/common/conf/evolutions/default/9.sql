# --- Rename in ComponentResult errorMsg to message
# --- Merge in StudyResult errorMsg and abortMsg to message

# --- !Ups
ALTER TABLE `ComponentResult` CHANGE `errorMsg` `message` varchar(255) DEFAULT NULL;
ALTER TABLE `StudyResult` ADD COLUMN  `message` varchar(255) DEFAULT NULL;
UPDATE `StudyResult` SET `message` = COALESCE(`errorMsg`, `abortMsg`);
ALTER TABLE `StudyResult` DROP COLUMN `abortMsg`;
ALTER TABLE `StudyResult` DROP COLUMN `errorMsg`;

# --- !Downs
# --- not supported
