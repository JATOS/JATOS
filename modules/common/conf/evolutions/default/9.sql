# --- Rename in ComponentResult errorMsg to message
# --- Merge in StudyResult errorMsg and abortMsg to message

# --- !Ups
ALTER TABLE `ComponentResult` CHANGE `errorMsg` `message` varchar(255) DEFAULT NULL;
ALTER TABLE `StudyResult` ADD COLUMN  `message` varchar(255) DEFAULT NULL;
UPDATE `StudyResult` SET `message` = IF(`errorMsg` IS NULL AND `abortMsg` IS NULL, NULL, CONCAT(COALESCE(`errorMsg` ,''), COALESCE(`abortMsg` ,'')));
ALTER TABLE `StudyResult` DROP COLUMN `abortMsg`, DROP COLUMN `errorMsg`;

# --- !Downs
# --- not supported
