# --- Add email to User table

# --- !Ups
ALTER TABLE `User` ADD `email` varchar(1023) DEFAULT NULL;


# --- !Downs
# --- not supported
