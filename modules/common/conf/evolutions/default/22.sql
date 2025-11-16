# --- Add row quotaReached to ComponentResult and StudyResult tables

# --- !Ups

ALTER TABLE `ComponentResult` ADD COLUMN  `quotaReached` tinyint(1) NOT NULL;
ALTER TABLE `StudyResult` ADD COLUMN  `quotaReached` tinyint(1) NOT NULL;

# --- !Downs
# --- not supported
