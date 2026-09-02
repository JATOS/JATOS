# Add groupSessionWriteScope column to Study table.
# Migration to add ON DELETE CASCADE and rename keys with FK_ to something meaningful.
# NOTE: Workers can belong to multiple batches. In BatchWorkerMap we delete cascade on Batch but not on Worker.

# --- !Ups

ALTER TABLE `Study` ADD COLUMN `groupSessionWriteScope` varchar(255) DEFAULT 'SHARED';

ALTER TABLE `ComponentResult`
    DROP FOREIGN KEY `FK_qxb7hfq7d4vaf9r5vjvnxpuwm`;
ALTER TABLE `ComponentResult`
    DROP INDEX `FK_qxb7hfq7d4vaf9r5vjvnxpuwm`;
ALTER TABLE `ComponentResult`
    ADD KEY `FK_component_result_component` (`component_id`);
ALTER TABLE `ComponentResult`
    ADD CONSTRAINT `FK_component_result_component`
        FOREIGN KEY (`component_id`) REFERENCES `Component` (`id`) ON DELETE CASCADE;

ALTER TABLE `ComponentResult`
    DROP FOREIGN KEY `FK_eevh468dxdqmcwsu8cqm4i2et`;
ALTER TABLE `ComponentResult`
    DROP INDEX `FK_eevh468dxdqmcwsu8cqm4i2et`;
ALTER TABLE `ComponentResult`
    ADD KEY `FK_component_result_study_result` (`studyResult_id`);
ALTER TABLE `ComponentResult`
    ADD CONSTRAINT `FK_component_result_study_result`
        FOREIGN KEY (`studyResult_id`) REFERENCES `StudyResult` (`id`) ON DELETE CASCADE;

ALTER TABLE `StudyResult`
    DROP FOREIGN KEY `FK_lsp7qm39v4t18he9jbgy4b1w5`;
ALTER TABLE `StudyResult`
    DROP INDEX `FK_lsp7qm39v4t18he9jbgy4b1w5`;
ALTER TABLE `StudyResult`
    ADD KEY `FK_study_result_batch` (`batch_id`);
ALTER TABLE `StudyResult`
    ADD CONSTRAINT `FK_study_result_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`) ON DELETE CASCADE;

ALTER TABLE `StudyResult`
    DROP FOREIGN KEY `FK_iiln24n58g3b1mxx3vupmg36h`;
ALTER TABLE `StudyResult`
    DROP INDEX `FK_iiln24n58g3b1mxx3vupmg36h`;
ALTER TABLE `StudyResult`
    ADD KEY `FK_study_result_study` (`study_id`);
ALTER TABLE `StudyResult`
    ADD CONSTRAINT `FK_study_result_study`
        FOREIGN KEY (`study_id`) REFERENCES `Study` (`id`) ON DELETE CASCADE;

ALTER TABLE `StudyResult`
    DROP FOREIGN KEY `FK_dggkq2gf4lsibvfxqrc25r8m6`;
ALTER TABLE `StudyResult`
    DROP INDEX `FK_dggkq2gf4lsibvfxqrc25r8m6`;
ALTER TABLE `StudyResult`
    ADD KEY `FK_study_result_worker` (`worker_id`);
ALTER TABLE `StudyResult`
    ADD CONSTRAINT `FK_study_result_worker`
        FOREIGN KEY (`worker_id`) REFERENCES `Worker` (`id`) ON DELETE CASCADE;

ALTER TABLE `StudyResult`
    DROP FOREIGN KEY `FK_2vbvsrpwxwnqbd0rud8kfr9ur`;
ALTER TABLE `StudyResult`
    DROP INDEX `FK_2vbvsrpwxwnqbd0rud8kfr9ur`;
ALTER TABLE `StudyResult`
    ADD KEY `FK_studyresult_activegroupmember` (`activeGroupMember_id`);
ALTER TABLE `StudyResult`
    ADD CONSTRAINT `FK_studyresult_activegroupmember`
        FOREIGN KEY (`activeGroupMember_id`) REFERENCES `GroupResult` (`id`);

ALTER TABLE `StudyResult`
    DROP FOREIGN KEY `FK_7052aavudt8sm5b6a3lhqn4uu`;
ALTER TABLE `StudyResult`
    DROP INDEX `FK_7052aavudt8sm5b6a3lhqn4uu`;
ALTER TABLE `StudyResult`
    ADD KEY `FK_studyresult_historygroupmember` (`historyGroupMember_id`);
ALTER TABLE `StudyResult`
    ADD CONSTRAINT `FK_studyresult_historygroupmember`
        FOREIGN KEY (`historyGroupMember_id`) REFERENCES `GroupResult` (`id`);

ALTER TABLE `GroupResult`
    DROP FOREIGN KEY `FK_g1hsnkt6f7jp8ulpne7h87pi1`;
ALTER TABLE `GroupResult`
    DROP INDEX `FK_g1hsnkt6f7jp8ulpne7h87pi1`;
ALTER TABLE `GroupResult`
    ADD KEY `FK_group_result_batch` (`batch_id`);
ALTER TABLE `GroupResult`
    ADD CONSTRAINT `FK_group_result_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`) ON DELETE CASCADE;

ALTER TABLE `Batch`
    DROP FOREIGN KEY `FK_80kwrl4v39mbxsw13mg09vnbi`;
ALTER TABLE `Batch`
    DROP INDEX `FK_80kwrl4v39mbxsw13mg09vnbi`;
ALTER TABLE `Batch`
    ADD KEY `FK_batch_study` (`study_id`);
ALTER TABLE `Batch`
    ADD CONSTRAINT `FK_batch_study`
        FOREIGN KEY (`study_id`) REFERENCES `Study` (`id`) ON DELETE CASCADE;

ALTER TABLE `Component`
    DROP FOREIGN KEY `FK_hv7ffe3qq68l092inojua2sxa`;
ALTER TABLE `Component`
    DROP INDEX `FK_hv7ffe3qq68l092inojua2sxa`;
ALTER TABLE `Component`
    ADD KEY `FK_component_study` (`study_id`);
ALTER TABLE `Component`
    ADD CONSTRAINT `FK_component_study`
        FOREIGN KEY (`study_id`) REFERENCES `Study` (`id`) ON DELETE CASCADE;

ALTER TABLE `StudyLink`
    DROP FOREIGN KEY `FK1j7tlwiv7tdfkb4671qfrxwi`;
ALTER TABLE `StudyLink`
    DROP INDEX `FK1j7tlwiv7tdfkb4671qfrxwi`;
ALTER TABLE `StudyLink`
    ADD KEY `FK_study_link_batch` (`batch_id`);
ALTER TABLE `StudyLink`
    ADD CONSTRAINT `FK_study_link_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`) ON DELETE CASCADE;

ALTER TABLE `BatchWorkerMap`
    DROP FOREIGN KEY `FK_suo6v5gv8gpvgwdvi6tbshtrj`;
ALTER TABLE `BatchWorkerMap`
    ADD KEY `FK_batch_worker_map_batch` (`batch_id`);
ALTER TABLE `BatchWorkerMap`
    ADD CONSTRAINT `FK_batch_worker_map_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`) ON DELETE CASCADE;

ALTER TABLE `BatchWorkerMap`
    DROP FOREIGN KEY `FK_mbcbdskuq79ml24uipmioxu3s`;
ALTER TABLE `BatchWorkerMap`
    DROP INDEX `FK_mbcbdskuq79ml24uipmioxu3s`;
ALTER TABLE `BatchWorkerMap`
    ADD KEY `FK_batchworkermap_worker` (`worker_id`);
ALTER TABLE `BatchWorkerMap`
    ADD CONSTRAINT `FK_batchworkermap_worker`
        FOREIGN KEY (`worker_id`) REFERENCES `Worker` (`id`);

ALTER TABLE `ApiToken`
    DROP INDEX `FK_lghqbQuIHvMEqpdJHkjdQHYbe`;
ALTER TABLE `ApiToken`
    ADD KEY `FK_api_token_user` (`user_username`);
ALTER TABLE `ApiToken`
    ADD CONSTRAINT `FK_api_token_user`
        FOREIGN KEY (`user_username`) REFERENCES `User` (`username`) ON DELETE CASCADE;

ALTER TABLE `StudyUserMap`
    DROP FOREIGN KEY `FK_d3uknug3vjrsetf527b7uplcd`;
ALTER TABLE `StudyUserMap`
    DROP INDEX `FK_d3uknug3vjrsetf527b7uplcd`;
ALTER TABLE `StudyUserMap`
    ADD KEY `FK_study_user_map_user` (`user_username`);
ALTER TABLE `StudyUserMap`
    ADD CONSTRAINT `FK_study_user_map_user`
        FOREIGN KEY (`user_username`) REFERENCES `User` (`username`) ON DELETE CASCADE;

ALTER TABLE `StudyUserMap`
    DROP FOREIGN KEY `FK_povwnfi99xfcfiyloh0ufv7hb`;
ALTER TABLE `StudyUserMap`
    DROP INDEX `FK_povwnfi99xfcfiyloh0ufv7hb`;
ALTER TABLE `StudyUserMap`
    ADD KEY `FK_studyusermap_study` (`study_id`);
ALTER TABLE `StudyUserMap`
    ADD CONSTRAINT `FK_studyusermap_study`
        FOREIGN KEY (`study_id`) REFERENCES `Study` (`id`);

ALTER TABLE `User`
    DROP FOREIGN KEY `FK_pk77d8680811astbnoae923x1`;
ALTER TABLE `User`
    DROP INDEX `FK_pk77d8680811astbnoae923x1`;
ALTER TABLE `User`
    ADD KEY `FK_user_jatosworker` (`worker_id`);
ALTER TABLE `User`
    ADD CONSTRAINT `FK_user_jatosworker`
        FOREIGN KEY (`worker_id`) REFERENCES `Worker` (`id`) ON DELETE CASCADE;

ALTER TABLE `Batch_allowedWorkerTypes`
    DROP FOREIGN KEY `FK_kwj5qdspmur6iqdgtb7kjvdg2`;
ALTER TABLE `Batch_allowedWorkerTypes`
    DROP INDEX `FK_kwj5qdspmur6iqdgtb7kjvdg2`;
ALTER TABLE `Batch_allowedWorkerTypes`
    ADD KEY `FK_batch_allowedworkertypes_batch` (`batch_id`);
ALTER TABLE `Batch_allowedWorkerTypes`
    ADD CONSTRAINT `FK_batch_allowedworkertypes_batch`
        FOREIGN KEY (`batch_id`) REFERENCES `Batch` (`id`);

# --- !Downs
# --- not supported