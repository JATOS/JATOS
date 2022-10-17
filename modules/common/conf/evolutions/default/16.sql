# --- Add ApiToken table

# --- !Ups
CREATE TABLE `ApiToken` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `tokenHash` varchar(255) NOT NULL,
  `name` longtext NOT NULL,
  `user_username` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `expires` int(11) DEFAULT NULL,
  `active` tinyint(1) NOT NULL,
  PRIMARY KEY (`id`)
);

ALTER TABLE `ApiToken` ADD KEY `FK_lghqbQuIHvMEqpdJHkjdQHYbe` (`user_username`);
ALTER TABLE `ApiToken` ADD CONSTRAINT `FK_lghqbQuIHvMEqpdJHkjdQHYbe` FOREIGN KEY (`user_username`) REFERENCES `User` (`username`);

# --- !Downs
# --- not supported
