package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.FieldValidationException;
import exceptions.common.JatosException;
import general.common.JpaTransactions;
import json.common.DomainJsonMapper;
import models.common.Study;
import models.gui.StagedStudySource;
import models.gui.StudySourceProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static general.common.ApiEnvelope.ErrorCode.IO_ERROR;
import static models.common.StudySource.Type.LOCAL_FOLDER;

/**
 * Coordinates local-folder study creation and source configuration.
 */
@Singleton
public class LocalFolderStudyImport {

    private final LocalStudySourceIO localStudySourceIO;
    private final StudyImportService studyImportService;
    private final StudySourceService studySourceService;
    private final JpaTransactions transactions;
    private final DomainJsonMapper domainJsonMapper;

    @Inject
    public LocalFolderStudyImport(LocalStudySourceIO localStudySourceIO,
                                  StudyImportService studyImportService,
                                  StudySourceService studySourceService,
                                  JpaTransactions transactions,
                                  DomainJsonMapper domainJsonMapper) {
        this.localStudySourceIO = localStudySourceIO;
        this.studyImportService = studyImportService;
        this.studySourceService = studySourceService;
        this.transactions = transactions;
        this.domainJsonMapper = domainJsonMapper;
    }

    /**
     * Created study and feedback describing the content actually imported.
     */
    public record CreationResult(JsonNode study, String message) {}

    /**
     * Copies source content to temporary storage and imports the study.
     */
    public CreationResult createStudyFromLocalFolder(String localFolderPath) {
        try (StagedStudySource staged = stageLocalFolder(localFolderPath)) {
            Study preparedStudy = studyImportService.prepareStudyFromStagedSource(staged);
            JsonNode study = createLocalFolderStudyInTransaction(staged, preparedStudy, localFolderPath);
            String imported = staged.getProperties().isPresent()
                    ? (staged.getAssets().isPresent() ? "properties and assets" : "properties only")
                    : "assets only";
            return new CreationResult(study, "Study created from local folder: imported " + imported + ".");
        } catch (IOException e) {
            throw new JatosException("Could not create study from study source.", e, IO_ERROR);
        }
    }

    private StagedStudySource stageLocalFolder(String localFolderPath) {
        try {
            return localStudySourceIO.stage(localFolderPath);
        } catch (IOException e) {
            throw new FieldValidationException("localFolderPath", "Could not read the source folder: " + e.getMessage());
        }
    }

    private JsonNode createLocalFolderStudyInTransaction(StagedStudySource stagedStudySource, Study preparedStudy,
                                                         String localFolderPath) {
        try {
            return transactions.withTransaction(em -> {
                Study study = studyImportService.createStudyFromStagedSource(stagedStudySource, preparedStudy);
                StudySourceProperties props = new StudySourceProperties();
                props.setType(LOCAL_FOLDER);
                props.setLocalFolderPath(localFolderPath);
                studySourceService.save(study.getId(), props);
                JsonNode result = domainJsonMapper.studyAsJsonForApi(study, false, false);
                closeStagedSourceBeforeCommit(stagedStudySource);
                return result;
            });
        } catch (RuntimeException e) {
            // A uniqueness violation can occur at commit; query only after rollback.
            throw studyImportService.translateStudyCreationConflict(e, preparedStudy.getUuid());
        }
    }

    private void closeStagedSourceBeforeCommit(StagedStudySource staged) {
        // The outer resource block also covers failures before this point.
        try {
            staged.close();
        } catch (IOException e) {
            throw new JatosException("Could not clean up the staged study source.", e, IO_ERROR);
        }
    }

}
