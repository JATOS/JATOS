Add lastSeenDate for heart beat in v2.2.5 

# --- !Ups
ALTER TABLE `ComponentResult` ADD `lastSeenDate` datetime DEFAULT NULL;

# --- !Downs
ALTER TABLE `ComponentResult` DROP `lastSeenDate`;
