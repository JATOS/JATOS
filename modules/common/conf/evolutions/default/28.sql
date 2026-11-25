# Add session version to User table to allow session revocation on all devices

# --- !Ups
ALTER TABLE `User` ADD COLUMN `sessionVersion` BIGINT NOT NULL DEFAULT 0;

# --- !Downs
ALTER TABLE `User` DROP COLUMN `sessionVersion`;
