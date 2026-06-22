# --- Add secondary numeric ID to User table
# --- Add row openAiApiCount to StudyResult table

# --- !Ups
ALTER TABLE `StudyResult` ADD `openAiApiCount` INT NOT NULL DEFAULT 0;

ALTER TABLE `User` ADD COLUMN `id` BIGINT NULL;

CREATE TEMPORARY TABLE `user_id_map` (
    `username` VARCHAR(255) NOT NULL,
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (`id`)
);

INSERT INTO `user_id_map` (`username`) SELECT `username` FROM `User` ORDER BY `username`;

UPDATE `User` u SET `id` = (
    SELECT m.`id` FROM `user_id_map` m WHERE m.`username` = u.`username`
);

ALTER TABLE `User` ADD UNIQUE KEY `UK_m9r2kq7t1v8n3c6p0a4x5j2d` (`id`);
ALTER TABLE `User` MODIFY COLUMN `id` BIGINT AUTO_INCREMENT;

# --- !Downs
# --- not supported