# --- Add lastSeen to User table and create table LoginAttempt

# --- !Ups

ALTER TABLE `User` ADD `lastSeen` datetime DEFAULT NULL;

CREATE TABLE `LoginAttempt` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `date` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY (`username`)
) DEFAULT CHARSET=utf8;

# --- !Downs
# --- not supported
