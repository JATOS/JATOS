# Add URL query string parameters to StudyResult in v3.1.9

# --- !Ups
ALTER TABLE `StudyResult` ADD `urlQueryParameters` text;

# --- !Downs
ALTER TABLE `StudyResult` DROP `urlQueryParameters`;
