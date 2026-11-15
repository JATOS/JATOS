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
import models.gui.StudySourceSyncProperties;
import services.gui.AuthorizationService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.StudySource.Type.LOCAL_FOLDER;
import static models.common.StudySource.Type.NONE;

/**
 * Synchronizes content from the configured study source without changing source settings or credentials.
 */
@Singleton
public class StudySourceSync {

    private final JpaTransactions transactions;
    private final StudyDao studyDao;
    private final UserDao userDao;
    private final AuthorizationService authorizationService;
    private final StudySourceSyncIO studySourceSyncIO;
    private final StudyLogger studyLogger;
    private final LocalFolderStudyImportExport localFolderStudyImportExport;

    @Inject
    public StudySourceSync(JpaTransactions transactions,
                           StudyDao studyDao,
                           UserDao userDao,
                           AuthorizationService authorizationService,
                           StudySourceSyncIO studySourceSyncIO,
                           StudyLogger studyLogger,
                           LocalFolderStudyImportExport localFolderStudyImportExport) {
        this.transactions = transactions;
        this.studyDao = studyDao;
        this.userDao = userDao;
        this.authorizationService = authorizationService;
        this.studySourceSyncIO = studySourceSyncIO;
        this.studyLogger = studyLogger;
        this.localFolderStudyImportExport = localFolderStudyImportExport;
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
    public SyncPreview previewImport(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        String localFolderPath = getLocalFolder(study);
        try {
            var preview = localFolderStudyImportExport.previewImport(study, localFolderPath);
            return new SyncPreview(LOCAL_FOLDER,
                    localFolderPath,
                    preview.propertiesAvailable(),
                    preview.assetsAvailable());
        } catch (IOException e) {
            throw new ImportExportException("Could not read the study source: " + e.getMessage());
        }
    }

    /**
     * Previews the destination within the caller's transaction and study-access authorization.
     */
    public SyncPreview previewExport(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        String localFolderPath = getLocalFolder(study);
        try {
            var preview = localFolderStudyImportExport.previewExport(study, localFolderPath);
            return new SyncPreview(
                    LOCAL_FOLDER,
                    preview.folder().toString(),
                    true,
                    preview.assetsAvailable());
        } catch (IOException e) {
            throw new ImportExportException("Could not preview local-folder export: " + e.getMessage());
        }
    }

    /**
     * Owns its transaction so filesystem recovery finishes before releasing the synchronization lock. The caller must
     * validate the synchronization properties before calling this method.
     */
    public SyncResult importFromFolder(long studyId, StudySourceSyncProperties props) {
        try (var ignored = studySourceSyncIO.lock(studyId)) {
            return transactions.withNewTransaction(em -> {
                Study study = getAuthorizedStudy(studyId);
                String folder = getLocalFolder(study);

                try {
                    localFolderStudyImportExport.importFromLocalFolder(study, folder, props);
                    User signedinUser = Context.current().args().get(SIGNEDIN_USER);

                    String scope = props.isProperties() ? (props.isAssets() ? "properties and assets" : "properties") : "assets";
                    String studyUuid = study.getUuid();
                    String userName = signedinUser.getName();
                    transactions.afterCommit("Log study synchronization",
                            () -> studyLogger.log(studyUuid, userName, "Imported " + scope + " from local folder: " + folder));
                    return new SyncResult(study.getId(), props.isProperties(), props.isAssets(),
                            "Imported " + scope + " from local folder.");
                } catch (IOException e) {
                    throw new ImportExportException("Could not synchronize study source: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new ImportExportException("Could not access the study synchronization lock.");
        }
    }

    /**
     * Exports validated selections while holding the synchronization lock through transaction completion and recovery.
     */
    public SyncResult exportToFolder(long studyId, StudySourceSyncProperties props) {
        try (var ignored = studySourceSyncIO.lock(studyId)) {
            return transactions.withNewTransaction(em -> {
                Study study = getAuthorizedStudy(studyId);
                String folder = getLocalFolder(study);
                User user = Context.current().args().get(SIGNEDIN_USER);
                try {
                    localFolderStudyImportExport.exportToLocalFolder(study, folder, props);
                } catch (IOException e) {
                    throw new ImportExportException("Could not export study to local folder: " + e.getMessage());
                }
                String scope = props.isProperties() ? (props.isAssets() ? "properties and assets" : "properties") : "assets";
                String studyUuid = study.getUuid();
                String userName = user.getName();
                String message = "Exported " + scope + " to local folder.";
                transactions.afterCommit("Log local-folder export", () -> studyLogger.log(studyUuid, userName, "Exported " + scope + " to local folder: " + folder));
                return new SyncResult(studyId, props.isProperties(), props.isAssets(), message);
            });
        } catch (IOException e) {
            throw new ImportExportException("Could not access the study synchronization lock.");
        }
    }

    private Study getAuthorizedStudy(long id) {
        User actor = Context.current().args().get(SIGNEDIN_USER);
        // Use the same lock order as source configuration and read current membership under the study lock.
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


}
