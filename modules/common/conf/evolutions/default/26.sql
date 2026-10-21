# Add study ownership and migrate existing membership before enforcing constraints.
# Membership has no insertion order. Choose the first username in database sort order.
# Repair memberless studies. If admin is missing, the membership FK fails the evolution.

# --- !Ups
ALTER TABLE `Study` ADD COLUMN `owner_username` varchar(255);

UPDATE `Study` SET `owner_username` = (
    SELECT MIN(`user_username`) FROM `StudyUserMap` WHERE `study_id` = `Study`.`id`
);

INSERT INTO `StudyUserMap` (`study_id`, `user_username`)
    SELECT `id`, 'admin' FROM `Study` WHERE `owner_username` IS NULL;
UPDATE `Study` SET `owner_username` = 'admin' WHERE `owner_username` IS NULL;

ALTER TABLE `Study` MODIFY COLUMN `owner_username` varchar(255) NOT NULL;
ALTER TABLE `Study` ADD CONSTRAINT `FK_study_owner_user`
    FOREIGN KEY (`owner_username`) REFERENCES `User` (`username`);

# --- !Downs
# --- not supported
