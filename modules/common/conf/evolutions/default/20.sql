# --- Insert admin user

# --- !Ups

ALTER TABLE `User` ADD COLUMN `lastVisitedPageUrl` varchar(255);

# --- !Downs
# --- not supported
