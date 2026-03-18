# --- Add secondary numeric ID to User migrate references
# --- Add row quotaReached to StudyResult table

# --- !Ups

ALTER TABLE `User` ADD COLUMN `id` BIGINT(29) NOT NULL AUTO_INCREMENT;
ALTER TABLE `User` ADD UNIQUE KEY `UK_m9r2kq7t1v8n3c6p0a4x5j2d` (`id`);

ALTER TABLE `StudyResult` ADD `openAiApiCount` INT NOT NULL DEFAULT 0;

# --- !Downs
# --- not supported