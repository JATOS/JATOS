Add new columns in Batch

# --- !Ups
ALTER TABLE `Batch` ADD `comments` longtext;
ALTER TABLE `Batch` ADD `jsonData` longtext;
ALTER TABLE `Batch` ADD `batchSessionData` longtext;
ALTER TABLE `Batch` ADD `batchSessionVersion` bigint(20) NOT NULL;

# --- !Downs
# not supported
