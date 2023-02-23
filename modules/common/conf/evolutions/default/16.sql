# --- Add dataShort and dataSize to ComponentResult table; Add indices to uuid of StudyResult, Batch and Component

# --- !Ups
ALTER TABLE `ComponentResult` ADD `dataShort` varchar(1023) DEFAULT NULL;
ALTER TABLE `ComponentResult` ADD `dataSize` bigint(20) DEFAULT NULL;

ALTER TABLE `StudyResult` ADD UNIQUE KEY `FKzl0vqfy8qoopgrkkcp1q5qja` (`uuid`);
ALTER TABLE `Batch` ADD UNIQUE KEY `FKlskzulbu7gz7mktw9zv6dso4` (`uuid`);

# --- !Downs
# --- not supported
