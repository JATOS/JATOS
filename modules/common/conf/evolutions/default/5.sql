Add lastSeenDate for heart beat in v2.2.5 

# --- !Ups
ALTER TABLE `Batch` ADD `comments` longtext;
ALTER TABLE `Batch` ADD `batchSessionData` longtext;
ALTER TABLE `Batch` ADD `batchSessionVersion` bigint(20) NOT NULL;

# --- !Downs
# not supported
