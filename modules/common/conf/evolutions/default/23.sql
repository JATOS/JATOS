# --- Add row quotaReached to ComponentResult and StudyResult tables

# --- !Ups

ALTER TABLE `StudyResult` ADD `openAiApiCount` INT NOT NULL DEFAULT 0;

# --- !Downs
# --- not supported
