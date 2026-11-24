package services.gui.importexport;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.common.ForbiddenException;
import exceptions.common.BadRequestException;
import exceptions.common.ConflictException;
import exceptions.common.ImportExportException;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import general.common.JpaTransactions;
import models.common.Component;
import models.common.Study;
import models.gui.StagedStudySource;
import models.gui.StudySourceSyncProperties;
import org.hibernate.exception.ConstraintViolationException;
import services.gui.BatchService;
import services.gui.ComponentService;
import services.gui.StudyService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static exceptions.common.JatosException.unchecked;

/**
 * Validates and applies imported study content independently of its source.
 */
@Singleton
public class StudyImportService {

    private final JpaTransactions transactions;
    private final StudyService studyService;
    private final BatchService batchService;
    private final ComponentService componentService;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyDeserializer studyDeserializer;

    @Inject
    public StudyImportService(JpaTransactions transactions,
                              StudyService studyService,
                              BatchService batchService,
                              ComponentService componentService,
                              IOUtils ioUtils,
                              StudyDao studyDao,
                              ComponentDao componentDao,
                              StudyDeserializer studyDeserializer) {
        this.transactions = transactions;
        this.studyService = studyService;
        this.batchService = batchService;
        this.componentService = componentService;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyDeserializer = studyDeserializer;
    }

    /**
     * Validates the staged identity and selected content, then applies selected properties. The caller replaces
     * selected assets in the same transaction. UUID validation also applies to assets-only imports.
     */
    public void importPropertiesFromStagedSource(Study study, StagedStudySource staged, StudySourceSyncProperties props) {
        Study imported = readStudyFromSource(study, staged);
        if (props.isProperties() && imported == null)
            throw new BadRequestException("The source has no .jas file. Only assets can be synchronized.");
        if (props.isAssets() && staged.getAssets().isEmpty())
            throw new BadRequestException("The source has no assets/ directory. Existing assets are preserved.");
        if (props.isProperties()) {
            checkForExistingComponents(imported);
            studyService.updateStudyWithoutDirName(study, imported);
            updateStudysComponents(study, imported);
        }
    }

    /** Reads optional source properties and rejects a different study UUID. */
    public Study readStudyFromSource(Study target, StagedStudySource stagedSource) {
        if (stagedSource.getProperties().isEmpty()) return null;
        Study importedStudy = deserializeStudyProperties(stagedSource.getProperties().orElseThrow());
        if (!target.getUuid().equals(importedStudy.getUuid()))
            throw new BadRequestException("The source .jas UUID differs from this study's UUID. Synchronization was aborted.");
        return importedStudy;
    }

    /**
     * Rejects imported components whose UUID belongs to another study.
     */
    public void checkForExistingComponents(Study uploadedStudy) {
        componentDao.findUuidUsedByOtherStudy(uploadedStudy).ifPresent(componentUuid -> {
            throw new ForbiddenException("An component of the imported study has the same UUID (" + componentUuid
                    + ") as an existing component.");
        });
    }

    public RuntimeException translateStudyCreationConflict(RuntimeException failure, String studyUuid) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException && studyDao.findByUuid(studyUuid).isPresent()) {
                return studySourceUuidConflict();
            }
        }
        return failure;
    }

    private ConflictException studySourceUuidConflict() {
        return new ConflictException(
                "A study with this UUID already exists. If you own this study, you can change its source settings to use this folder.",
                ErrorCode.INVALID_REQUEST);
    }

    /**
     * Reads study properties from the staged source, or uses defaults when no .jas file exists.
     */
    public Study prepareStudyFromStagedSource(StagedStudySource staged) {
        Study study;
        if (staged.getProperties().isPresent()) {
            study = deserializeStudyProperties(staged.getProperties().get());
        } else {
            study = new Study();
            study.setTitle("New study");
            study.setDirName(study.getUuid());
        }
        // Database identities and memberships belong to this instance, never to the source.
        study.setId(null);
        study.getUserList().clear();
        study.getComponentList().forEach(component -> component.setId(null));
        study.getBatchList().forEach(batch -> batch.setId(null));
        return study;
    }

    /**
     * Creates a study from the staged properties and assets.
     * The caller provides the transaction and cleans up the staged source.
     */
    public Study createStudyFromStagedSource(StagedStudySource staged, Study study) {
        if (studyDao.findByUuid(study.getUuid()).isPresent()) {
            throw studySourceUuidConflict();
        }
        checkForExistingComponents(study);
        return createNewStudy(study, staged.getAssets().orElse(null), true);
    }

    /**
     * Reads and validates study properties from a .jas file.
     */
    public Study deserializeStudyProperties(Path studyFile) {
        Study study = studyDeserializer.deserialize(studyFile);
        studyService.validate(study);
        try {
            study.getComponentList().forEach(componentService::validate);
            study.getBatchList().forEach(batchService::validate);
        } catch (ValidationException e) {
            throw new ImportExportException(e.getMessage());
        }
        return study;
    }

    /**
     * Adds or updates imported components and inactivates omitted components.
     */
    public void updateStudysComponents(Study currentStudy, Study updatedStudy) {
        // Clear the list and rebuild it from the updated study
        List<Component> currentComponentList = new ArrayList<>(currentStudy.getComponentList());
        currentStudy.getComponentList().clear();

        for (Component updatedComponent : updatedStudy.getComponentList()) {
            Component currentComponent = null;
            // Find both matching components with the same UUID
            for (Component tempComponent : currentComponentList) {
                if (tempComponent.getUuid().equals(updatedComponent.getUuid())) {
                    currentComponent = tempComponent;
                    break;
                }
            }
            if (currentComponent != null) {
                componentService.updateProperties(currentComponent, updatedComponent);
                currentStudy.addComponent(currentComponent);
                currentComponentList.remove(currentComponent);
            } else {
                // If the updated component doesn't exist in the current study, add it.
                componentService.createAndPersistComponent(currentStudy, updatedComponent);
            }
        }

        // Check whether any component from the current study is left that isn't in the updated study. Add them to the
        // end of the list and put them into inactive (we don't remove them because they could be associated with results)
        for (Component currentComponent : currentComponentList) {
            currentComponent.setActive(false);
            currentStudy.addComponent(currentComponent);
        }

        studyDao.merge(currentStudy);
    }

    /**
     * Creates a study and installs its assets within the caller's transaction.
     * Rollback removes only the assets allocated for this study.
     */
    public Study createNewStudy(Study study, Path assets, boolean renameAssets) {
        if (renameAssets) {
            study.setDirName(unchecked(() -> ioUtils.reserveStudyAssetsDirectory(study.getDirName(), study.getUuid())));
        } else {
            try {
                ioUtils.createStudyAssetsDir(study.getDirName());
            } catch (FileAlreadyExistsException e) {
                throw new ForbiddenException("Cannot import study: a study assets directory with the same name exists already, but 'renameAssets' is set to false.");
            } catch (IOException e) {
                throw new JatosException(e);
            }
        }
        String dirName = study.getDirName();
        transactions.afterCompletion("Clean up assets after failed study import", () -> {},
                () -> ioUtils.removeStudyAssetsDir(dirName));
        if (assets != null) unchecked(() -> ioUtils.copyStagedStudyAssets(assets, dirName));
        return studyService.createAndPersistStudy(study);
    }

}
