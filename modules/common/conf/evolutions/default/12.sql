# --- Add column 'authMethod' and rename email to 'username' in User table

# --- !Ups
ALTER TABLE `User` CHANGE `email` `username` varchar(255) NOT NULL;
ALTER TABLE `StudyUserMap` CHANGE `user_email` `user_username` varchar(255) NOT NULL;
ALTER TABLE `User_roleList` CHANGE `User_email` `User_username` varchar(255) NOT NULL;
ALTER TABLE `User` ADD COLUMN `authMethod` varchar(255) NOT NULL DEFAULT 'DB';

# --- !Downs
# --- not supported
