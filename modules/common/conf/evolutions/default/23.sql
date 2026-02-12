# --- Add secondary numeric ID to User migrate references

# --- !Ups

ALTER TABLE `User` ADD COLUMN `id` BIGINT(29) NOT NULL AUTO_INCREMENT;
ALTER TABLE `User` ADD UNIQUE KEY `UK_m9r2kq7t1v8n3c6p0a4x5j2d` (`id`);

# --- !Downs
# --- not supported