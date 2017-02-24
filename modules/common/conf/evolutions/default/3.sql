Add roles to user to allow authorization

# --- !Ups
CREATE TABLE `User_roleList` (
  `User_email` varchar(255) NOT NULL,
  `roleList` varchar(255) DEFAULT NULL
) DEFAULT CHARSET=utf8;

ALTER TABLE `User_roleList` ADD KEY `FK9q91gwu0njssl15fn116efivn` (`User_email`);
ALTER TABLE `User_roleList` ADD CONSTRAINT `FK9q91gwu0njssl15fn116efivn` FOREIGN KEY (`User_email`) REFERENCES `User` (`email`);

INSERT INTO User_roleList VALUES ('admin', 'ADMIN');
INSERT INTO User_roleList (User_email, roleList) SELECT email, 'USER' FROM User;

# --- !Downs
DROP TABLE IF EXISTS `User_roleList`;
