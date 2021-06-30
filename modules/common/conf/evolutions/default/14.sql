# --- Add table StudyLink, add `allowPreview` to table Study and `uuid` to StudyResult

# --- !Ups
CREATE TABLE `StudyLink` (
  `id` varchar(255) NOT NULL,
  `workerType` varchar(255) DEFAULT NULL,
  `batch_id` bigint(20) DEFAULT NULL,
  `worker_id` bigint(20) DEFAULT NULL,
  `active` tinyint(1) NOT NULL,
  PRIMARY KEY (`id`)
) DEFAULT CHARSET=utf8;

ALTER TABLE `Study` ADD `allowPreview` tinyint(1) DEFAULT 0;
ALTER TABLE `StudyResult` ADD `uuid` varchar(255);
ALTER TABLE `StudyResult` ADD `studyLink_id` varchar(255) DEFAULT NULL;

ALTER TABLE `StudyLink` ADD KEY `FK1j7tlwiv7tdfkb4671qfrxwi` (`batch_id`);
ALTER TABLE `StudyLink` ADD KEY `FK40p56yjtyha180u11l8al3xbv` (`worker_id`);
ALTER TABLE `StudyLink` ADD CONSTRAINT `FK1j7tlwiv7tdfkb4671qfrxwi` FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`);
ALTER TABLE `StudyLink` ADD CONSTRAINT `FK40p56yjtyha180u11l8al3xbv` FOREIGN KEY (`worker_id`) REFERENCES `Worker` (`id`);



# --- !Downs
# --- not supported
