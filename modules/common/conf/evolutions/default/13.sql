# --- Add column 'lastLogin' to User table and 'active' to User table and Study table

# --- !Ups
ALTER TABLE `User` ADD COLUMN `lastLogin` datetime DEFAULT NULL;
ALTER TABLE `User` ADD COLUMN `active` tinyint(1) DEFAULT 1 NOT NULL;
ALTER TABLE `Study` ADD COLUMN `active` tinyint(1) DEFAULT 1 NOT NULL;


# --- !Downs
# --- not supported
