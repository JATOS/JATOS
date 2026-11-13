package services.gui.importexport;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.BadRequestException;
import exceptions.common.ImportExportException;
import general.common.JpaTransactions;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import models.gui.StagedStudySource;
import models.gui.StudySourceSyncProperties;
import services.gui.AuthorizationService;
import services.gui.StudyService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.StudySource.Type.LOCAL_FOLDER;
import static models.common.StudySource.Type.NONE;

/**
 * Synchronizes content from the configured source without changing source settings or credentials.
 */
@Singleton
public class StudySourceSync {

    private final JpaTransactions transactions;
    private final StudyDao studyDao;
    private final UserDao userDao;
    private final AuthorizationService authorizationService;
    private final LocalFolderStudySourceIO localFolderStudySourceIO;
    private final StudySourceSyncIO studySourceSyncIO;
    private final StudyImportService studyImportService;
    private final StudyService studyService;
    private final StudyLogger studyLogger;

    @Inject
    public StudySourceSync(JpaTransactions transactions,
                           StudyDao studyDao,
                           UserDao userDao,
                           AuthorizationService authorizationService,
                           LocalFolderStudySourceIO localFolderStudySourceIO,
                           StudySourceSyncIO studySourceSyncIO,
                           StudyImportService studyImportService,
                           StudyService studyService,
                           StudyLogger studyLogger) {
        this.transactions = transactions;
        this.studyDao = studyDao;
        this.userDao = userDao;
        this.authorizationService = authorizationService;
        this.localFolderStudySourceIO = localFolderStudySourceIO;
        this.studySourceSyncIO = studySourceSyncIO;
        this.studyImportService = studyImportService;
        this.studyService = studyService;
        this.studyLogger = studyLogger;
    }

    public record SyncPreview(StudySource.Type type,
                              String localFolderPath,
                              boolean propertiesAvailable,
                              boolean assetsAvailable) {}

    public record SyncResult(long studyId,
                             boolean propertiesUpdated,
                             boolean assetsUpdated,
                             String message) {}

    /**
     * Reads and validates a temporary snapshot within the caller’s transaction; no files survive this request. The
     * caller must authorize access to the unlocked study.
     */
    public SyncPreview preview(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        String localFolderPath = getLocalFolder(study);
        try (StagedStudySource staged = stage(localFolderPath)) {
            studySourceSyncIO.checkSourceOutsideAssets(localFolderPath, study.getDirName());
            extractStudyFromSource(study, staged);
            return new SyncPreview(
                    LOCAL_FOLDER,
                    localFolderPath,
                    staged.getProperties().isPresent(),
                    staged.getAssets().isPresent());
        } catch (IOException e) {
            throw new ImportExportException("Could not read the study source: " + e.getMessage());
        }
    }

    /**
     * Owns its transaction so filesystem recovery finishes before releasing the synchronization lock. The caller must
     * validate the synchronization properties before calling this method.
     */
    public SyncResult sync(long studyId, StudySourceSyncProperties props) {
        try (var ignored = studySourceSyncIO.lock(studyId)) {
            return transactions.withNewTransaction(em -> {
                Study study = getAuthorizedStudy(studyId);
                String folder = getLocalFolder(study);

                try (StagedStudySource staged = stage(folder)) {
                    studySourceSyncIO.checkSourceOutsideAssets(folder, study.getDirName());
                    Study imported = extractStudyFromSource(study, staged);
                    if (props.isProperties() && imported == null)
                        throw new BadRequestException("The source has no .jas file. Only assets can be synchronized.");
                    if (props.isAssets() && staged.getAssets().isEmpty())
                        throw new BadRequestException("The source has no assets/ directory. Existing assets are preserved.");

                    if (props.isProperties()) {
                        studyImportService.checkForExistingComponents(imported);
                        //noinspection DataFlowIssue
                        studyService.updateStudyWithoutDirName(study, imported);
                        studyImportService.updateStudysComponents(study, imported);
                    }
                    if (props.isAssets())
                        studySourceSyncIO.replaceAssets(staged.getAssets().orElseThrow(), study.getDirName());
                    User signedinUser = Context.current().args().get(SIGNEDIN_USER);
                    recordSync(study.getStudySource(), signedinUser.getUsername(), props);

                    String scope = props.isProperties() ? (props.isAssets() ? "properties and assets" : "properties") : "assets";
                    String studyUuid = study.getUuid();
                    String userName = signedinUser.getName();
                    transactions.afterCommit("Log study synchronization",
                            () -> studyLogger.log(studyUuid, userName, "Synchronized " + scope + " from local folder"));
                    return new SyncResult(study.getId(), props.isProperties(), props.isAssets(),
                            "Synchronized " + scope + " from local folder.");
                } catch (IOException e) {
                    throw new ImportExportException("Could not synchronize study source: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new ImportExportException("Could not access the study synchronization lock.");
        }
    }

    private StagedStudySource stage(String folder) {
        try {
            return localFolderStudySourceIO.stage(folder);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the study source: " + e.getMessage());
        }
    }

    private Study getAuthorizedStudy(long id) {
        User actor = Context.current().args().get(SIGNEDIN_USER);
        // Use the same lock order as source configuration, and read current membership under the study lock.
        User user = userDao.findByUsernameAndLock(actor);
        Study study = studyDao.findByIdWithSourceAndLock(id);
        authorizationService.canUserAccessStudy(study, user, true);
        return study;
    }

    private String getLocalFolder(Study study) {
        StudySource source = study.getStudySource();
        if (source == null || source.getType() == NONE)
            throw new BadRequestException("This study has no configured source.");
        if (source.getType() != LOCAL_FOLDER)
            throw new BadRequestException("Git synchronization is not available yet.");
        return source.getLocalFolderPath();
    }

    private Study extractStudyFromSource(Study target, StagedStudySource stagedSource) {
        if (stagedSource.getProperties().isEmpty()) return null;
        Study importedStudy = studyImportService.deserializeStudyProperties(stagedSource.getProperties().orElseThrow());
        if (!target.getUuid().equals(importedStudy.getUuid()))
            throw new BadRequestException("The source .jas UUID differs from this study's UUID. Synchronization was aborted.");
        return importedStudy;
    }

    private void recordSync(StudySource source, String username, StudySourceSyncProperties selection) {
        Timestamp now = Timestamp.from(Instant.now());
        if (selection.isProperties()) {
            source.setLastPropertiesSyncAt(now);
            source.setLastPropertiesSyncBy(username);
            source.setLastPropertiesSyncSourceType(source.getType());
            source.setLastPropertiesSyncSourceLocation(source.getLocalFolderPath());
            source.setLastPropertiesSyncGitCommit(null);
        }
        if (selection.isAssets()) {
            source.setLastAssetsSyncAt(now);
            source.setLastAssetsSyncBy(username);
            source.setLastAssetsSyncSourceType(source.getType());
            source.setLastAssetsSyncSourceLocation(source.getLocalFolderPath());
            source.setLastAssetsSyncGitCommit(null);
        }
    }
}
