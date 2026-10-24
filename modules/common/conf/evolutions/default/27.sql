# Instance-local study sources and HTTPS credentials. No prototype JSON migration.

# --- !Ups

CREATE TABLE `StudySource` (
    `study_id` bigint NOT NULL PRIMARY KEY,
    `type` varchar(16) NOT NULL DEFAULT 'NONE',
    `gitUrl` varchar(2048),
    `gitRef` varchar(255),
    `localFolderPath` varchar(4096),
    `anonymous` boolean NOT NULL DEFAULT false,
    `lastPropertiesSyncAt` timestamp,
    `lastPropertiesSyncBy` varchar(255),
    `lastPropertiesSyncSourceType` varchar(16),
    `lastPropertiesSyncSourceLocation` varchar(4096),
    `lastPropertiesSyncGitCommit` varchar(64),
    `lastAssetsSyncAt` timestamp,
    `lastAssetsSyncBy` varchar(255),
    `lastAssetsSyncSourceType` varchar(16),
    `lastAssetsSyncSourceLocation` varchar(4096),
    `lastAssetsSyncGitCommit` varchar(64),
    CONSTRAINT `FK_study_source_study` FOREIGN KEY (`study_id`) REFERENCES `Study` (`id`) ON DELETE CASCADE,
    CONSTRAINT `CK_study_source_type` CHECK (`type` IN ('NONE', 'GIT', 'LOCAL_FOLDER'))
);

CREATE TABLE `GitCredentials` (
    `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `study_source_id` bigint UNIQUE,
    `user_username` varchar(255) UNIQUE,
    `username` varchar(255) NOT NULL,
    `encryptedSecret` longtext NOT NULL,
    `origin` varchar(512) NOT NULL,
    `createdAt` timestamp NOT NULL,
    CONSTRAINT `FK_git_credentials_source` FOREIGN KEY (`study_source_id`) REFERENCES `StudySource` (`study_id`) ON DELETE CASCADE,
    CONSTRAINT `FK_git_credentials_user` FOREIGN KEY (`user_username`) REFERENCES `User` (`username`) ON DELETE CASCADE,
    CONSTRAINT `CK_git_credentials_owner` CHECK (
        (`study_source_id` IS NOT NULL AND `user_username` IS NULL) OR
        (`study_source_id` IS NULL AND `user_username` IS NOT NULL)
    )
);

# --- !Downs
# --- not supported
