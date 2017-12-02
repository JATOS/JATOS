# --- !Ups
UPDATE `StudyUserMap` SET user_email = LOWER(user_email);
UPDATE `User_roleList` SET User_email = LOWER(User_email);

# --- !Downs
# --- not supported
