# --- !Ups
SET FOREIGN_KEY_CHECKS = 0;

UPDATE `StudyUserMap` SET user_email = LOWER(user_email);
UPDATE `User_roleList` SET User_email = LOWER(User_email);
UPDATE `User` SET email = LOWER(email);

SET FOREIGN_KEY_CHECKS = 1;

# --- !Downs
# --- not supported
