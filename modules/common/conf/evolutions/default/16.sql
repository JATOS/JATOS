# --- Add dataShort and dataSize to ComponentResult table

# --- !Ups
ALTER TABLE `ComponentResult` ADD `dataShort` varchar(1023) DEFAULT NULL;
ALTER TABLE `ComponentResult` ADD `dataSize` bigint(20) DEFAULT NULL;


# --- !Downs
# --- not supported
