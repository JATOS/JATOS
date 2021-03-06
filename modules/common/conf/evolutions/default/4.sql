# Reverse User <-> JatosWorker relationship
# Currently: Worker -> User (via column user_email)
# Then: User -> Worker (via column worker_id)

# --- !Ups

ALTER TABLE `User` ADD `worker_id` bigint(20) DEFAULT NULL;
UPDATE `User` u SET `worker_id` = (SELECT `id` FROM `Worker` WHERE `user_email` = u.email);

ALTER TABLE `User` ADD KEY `FK_pk77d8680811astbnoae923x1` (`worker_id`);
ALTER TABLE `User` ADD CONSTRAINT `FK_pk77d8680811astbnoae923x1` FOREIGN KEY (`worker_id`) REFERENCES `Worker` (`id`);

ALTER TABLE `Worker` DROP FOREIGN KEY `FK_rvmm2rl58o8ui2tsq774o8rij`;
ALTER TABLE `Worker` DROP COLUMN `user_email`;

# --- !Downs
# Not supported