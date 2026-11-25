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
    private final GitStudyImportExport gitStudyImportExport;

    @Inject
    public StudySourceSync(JpaTransactions transactions,
                           StudyDao studyDao,
                           UserDao userDao,
                           AuthorizationService authorizationService,
                           StudySourceSyncIO studySourceSyncIO,
                           StudyLogger studyLogger,
                           LocalFolderStudyImportExport localFolderStudyImportExport,
                           GitStudyImportExport gitStudyImportExport) {
        this.transactions = transactions;
        this.studyDao = studyDao;
        this.userDao = userDao;
        this.authorizationService = authorizationService;
        this.studySourceSyncIO = studySourceSyncIO;
        this.studyLogger = studyLogger;
        this.localFolderStudyImportExport = localFolderStudyImportExport;
        this.gitStudyImportExport = gitStudyImportExport;
    }

    public record LocalFolderPreview(String localFolderPath,
                                     boolean propertiesAvailable,
                                     boolean assetsAvailable) {}

    public record GitPreview(
            String gitUrl, // Configured repository URL
            String gitRef, // For pull: configured branch/tag or empty for default branch. For push: resolved branch.
            String commitId, // Full commit ID. For push, the destination’s current commit, not the future new commit.
            String credentialSource, // Where the selected credentials come from: study, owner, global, or anonymous.
            boolean propertiesAvailable,
            boolean assetsAvailable) {}

    // Used for local folder import/export and git pull (but not push)
    public record SyncResult(
            long studyId,
            boolean propertiesUpdated,
            boolean assetsUpdated,
            String message) {} // Human-readable success message for the GUI or API client.

    public record GitPushResult(
            long studyId,
            String commitId, // Resulting commit ID: the new commit, or the existing commit if nothing changed.
            boolean changed, // Whether selected content differed and a new commit was pushed.
            String message) {} // Human-readable success or “nothing changed” message.

    /**
     * Checks which properties and assets can be imported from the local folder.
     */
    public LocalFolderPreview previewImport(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        String localFolderPath = getLocalFolder(study);
        try {
            var preview = localFolderStudyImportExport.previewImport(study, localFolderPath);
            return new LocalFolderPreview(
                    localFolderPath,
                    preview.propertiesAvailable(),
                    preview.assetsAvailable());
        } catch (IOException e) {
            throw new ImportExportException("Could not read the study source: " + e.getMessage());
        }
    }

    /**
     * Checks which study content can be exported to the local folder.
     */
    public LocalFolderPreview previewExport(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        String localFolderPath = getLocalFolder(study);
        try {
            var preview = localFolderStudyImportExport.previewExport(study, localFolderPath);
            return new LocalFolderPreview(
                    preview.folder().toString(),
                    true,
                    preview.assetsAvailable());
        } catch (IOException e) {
            throw new ImportExportException("Could not preview local-folder export: " + e.getMessage());
        }
    }

    /**
     * Imports selected content from the local folder.
     */
    @SuppressWarnings("try")
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
     * Exports selected content to the local folder.
     */
    @SuppressWarnings("try")
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

    /**
     * Checks which properties and assets can be pulled from Git.
     */
    public GitPreview previewGitPull(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        try {
            return gitStudyImportExport.previewPull(study);
        } catch (IOException e) {
            throw new ImportExportException("Could not read or clean up the Git study source.");
        }
    }

    /**
     * Pulls selected content from Git into the study.
     */
    @SuppressWarnings("try")
    public SyncResult pullFromGit(long studyId, StudySourceSyncProperties props) {
        try (var ignored = studySourceSyncIO.lock(studyId)) {
            return transactions.withNewTransaction(em -> {
                Study study = getAuthorizedStudy(studyId);
                try {
                    String commit = gitStudyImportExport.pullFromGit(study, props);
                    String scope = props.isProperties() ? (props.isAssets() ? "properties and assets" : "properties") : "assets";
                    String uuid = study.getUuid();
                    User user = Context.current().args().get(SIGNEDIN_USER);
                    String userName = user.getName();
                    String message = "Pulled " + scope + " from Git at commit " + commit + ".";
                    String url = study.getStudySource().getGitUrl();
                    transactions.afterCommit("Log Git pull",
                            () -> studyLogger.log(uuid, userName, message + " Repository: " + url));
                    return new SyncResult(studyId, props.isProperties(), props.isAssets(), message);
                } catch (IOException e) {
                    throw new ImportExportException("Could not pull or clean up the Git study source.");
                }
            });
        } catch (IOException e) {
            throw new ImportExportException("Could not access the study synchronization lock.");
        }
    }

    /**
     * Checks the configured Git branch before exporting study content.
     */
    public GitPreview previewGitPush(long studyId) {
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        try {
            return gitStudyImportExport.previewPush(study);
        } catch (IOException e) {
            throw new ImportExportException("Could not preview or clean up the Git destination.");
        }
    }

    /**
     * Pushes selected content under the same study lock and member permissions as other synchronization.
     */
    @SuppressWarnings("try")
    public GitPushResult pushToGit(long studyId, StudySourceSyncProperties props) {
        try (var ignored = studySourceSyncIO.lock(studyId)) {
            return transactions.withNewTransaction(em -> {
                Study study = getAuthorizedStudy(studyId);
                try {
                    // Flush before the remote operation: a successful Git push cannot be rolled back by the database.
                    em.flush();
                    var result = gitStudyImportExport.pushToGit(study, props);
                    String scope = props.isProperties() ? (props.isAssets() ? "properties and assets" : "properties") : "assets";
                    String message = result.changed()
                            ? "Pushed " + scope + " to Git at commit " + result.commitId() + "."
                            : "Nothing changed. Git is already up to date at commit " + result.commitId() + ".";
                    String uuid = study.getUuid();
                    User user = Context.current().args().get(SIGNEDIN_USER);
                    String userName = user.getName();
                    String url = study.getStudySource().getGitUrl();
                    transactions.afterCommit("Log Git push",
                            () -> studyLogger.log(uuid, userName, message + " Repository: " + url));
                    return new GitPushResult(studyId, result.commitId(), result.changed(), message);
                } catch (IOException e) {
                    throw new ImportExportException("Could not push or clean up the Git destination. Check the remote branch before retrying.");
                }
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
            throw new BadRequestException("This study has no configured local-folder source.");
        return source.getLocalFolderPath();
    }


}
