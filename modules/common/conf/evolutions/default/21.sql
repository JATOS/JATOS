# --- Add row remoteAddress to LoginAttempt table

# --- !Ups

ALTER TABLE `LoginAttempt` ADD COLUMN `remoteAddress` varchar(255) NOT NULL;

# --- !Downs
# --- not supported
