# Add session ID to User table

# --- !Ups
ALTER TABLE `User` ADD `sessionId` varchar(255) DEFAULT NULL;

# --- !Downs
# not supported
