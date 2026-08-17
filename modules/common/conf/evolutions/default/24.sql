# Add groupSessionWriteScope column to Study table

# --- !Ups

ALTER TABLE `Study` ADD COLUMN `groupSessionWriteScope` varchar(255) DEFAULT 'SHARED';

# --- !Downs
# --- not supported