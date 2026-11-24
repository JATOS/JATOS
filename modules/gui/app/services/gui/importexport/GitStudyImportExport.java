package services.gui.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.UserDao;
import exceptions.common.BadRequestException;
import exceptions.common.ImportExportException;
import general.common.JpaTransactions;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import models.gui.StudySourceProperties;
import models.gui.StudySourceSyncProperties;
import services.gui.importexport.GitCredentialsResolver.ResolvedCredentials;
import services.gui.importexport.GitStudySourceIO.FetchedStudy;
import services.gui.importexport.GitStudySourceIO.PushResult;
import services.gui.importexport.StudySourceSync.GitPreview;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static exceptions.common.JatosException.unchecked;

/**
 * Creates studies from Git and pulls or pushes selected study content.
 */
@Singleton
public class GitStudyImportExport {

    private final JpaTransactions transactions;
    private final GitCredentialsResolver gitCredentialsResolver;
    private final GitStudySourceIO gitStudySourceIO;
    private final StudyImportService studyImportService;
    private final StudySourceService studySourceService;
    private final DomainJsonMapper domainJsonMapper;
    private final StudyLogger studyLogger;
    private final UserDao userDao;
    private final StudySourceSyncIO studySourceSyncIO;
    private final IOUtils ioUtils;

    @Inject
    public GitStudyImportExport(JpaTransactions transactions,
                                GitCredentialsResolver gitCredentialsResolver,
                                GitStudySourceIO gitStudySourceIO,
                                StudyImportService studyImportService,
                                StudySourceService studySourceService,
                                DomainJsonMapper domainJsonMapper,
                                StudyLogger studyLogger,
                                UserDao userDao,
                                StudySourceSyncIO studySourceSyncIO,
                                IOUtils ioUtils) {
        this.transactions = transactions;
        this.gitCredentialsResolver = gitCredentialsResolver;
        this.gitStudySourceIO = gitStudySourceIO;
        this.studyImportService = studyImportService;
        this.studySourceService = studySourceService;
        this.domainJsonMapper = domainJsonMapper;
        this.studyLogger = studyLogger;
        this.userDao = userDao;
        this.studySourceSyncIO = studySourceSyncIO;
        this.ioUtils = ioUtils;
    }

    /**
     * Fetches the Git source and checks which study content is available. Temporary files are removed before
     * returning.
     */
    public GitPreview previewPull(Study study) throws IOException {
        var source = gitSource(study);
        try (var credentials = gitCredentialsResolver.forExistingStudy(study);
             var fetchedStudy = gitStudySourceIO.stage(source.getGitUrl(), source.getGitRef(), credentials)) {
            studyImportService.readStudyFromSource(study, fetchedStudy.content());
            return new GitPreview(
                    source.getGitUrl(),
                    source.getGitRef(),
                    fetchedStudy.commitId(),
                    credentials.source(),
                    fetchedStudy.content().getProperties().isPresent(),
                    fetchedStudy.content().getAssets().isPresent());
        }
    }

    /**
     * Pulls selected study content from Git and returns the commit ID. Checks the UUID and available content on every
     * pull.
     */
    public String pullFromGit(Study study, StudySourceSyncProperties props) throws IOException {
        var source = gitSource(study);
        try (var credentials = gitCredentialsResolver.forExistingStudy(study);
             var fetchedStudy = gitStudySourceIO.stage(source.getGitUrl(), source.getGitRef(), credentials)) {
            studyImportService.importPropertiesFromStagedSource(study, fetchedStudy.content(), props);
            if (props.isAssets())
                studySourceSyncIO.replaceAssets(fetchedStudy.content().getAssets().orElseThrow(), study.getDirName());
            return fetchedStudy.commitId();
        }
    }

    /**
     * Fetches the destination branch and checks its study identity before pushing.
     */
    public GitPreview previewPush(Study study) throws IOException {
        var source = gitSource(study);
        try (var credentials = gitCredentialsResolver.forExistingStudy(study);
             var destination = gitStudySourceIO.preparePush(source.getGitUrl(), source.getGitRef(), credentials)) {
            studyImportService.readStudyFromSource(study, destination.fetchedStudy().content());
            return new GitPreview(
                    source.getGitUrl(),
                    destination.branch(),
                    destination.fetchedStudy().commitId(),
                    credentials.source(),
                    true,
                    Files.isDirectory(ioUtils.getStudyAssetsDir(study.getDirName(), false)));
        }
    }

    /**
     * Exports selected study content and pushes a commit to the configured branch.
     */
    public PushResult pushToGit(Study study, StudySourceSyncProperties props) throws IOException {
        var source = gitSource(study);
        try (var credentials = gitCredentialsResolver.forExistingStudy(study);
             var destination = gitStudySourceIO.preparePush(source.getGitUrl(), source.getGitRef(), credentials)) {
            var content = destination.fetchedStudy().content();
            studyImportService.readStudyFromSource(study, content);
            Path staged = Files.createDirectory(content.getDirectory().resolve("export"));
            String filename = content.getProperties().map(path -> path.getFileName().toString()).orElse("study.jas");
            if (props.isProperties()) {
                domainJsonMapper.studyAsJsonForIO(study, staged.resolve(filename));
            }
            if (props.isAssets()) {
                Path assets = ioUtils.getStudyAssetsDir(study.getDirName());
                LocalFolderStudySourceIO.copySourceDirectory(assets, assets, staged.resolve("assets"));
            }
            return gitStudySourceIO.push(destination, source.getGitUrl(), credentials, staged,
                    props.isProperties(), props.isAssets(), Context.current().args().get(SIGNEDIN_USER));
        }
    }

    private StudySource gitSource(Study study) {
        var source = study.getStudySource();
        if (source == null || source.getType() != StudySource.Type.GIT)
            throw new BadRequestException("This study has no configured Git source.");
        return source;
    }

    public record CreationResult(JsonNode study, String message) {}

    /**
     * Creates a study from Git and saves its source settings. An existing study UUID causes an error.
     */
    public CreationResult createStudyFromGit(StudySourceProperties props) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try (ResolvedCredentials access = resolveCredentials(props, signedinUser);
             FetchedStudy fetchedStudy = gitStudySourceIO.stage(props.getGitUrl(), props.getGitRef(), access)) {

            Study preparedStudy = studyImportService.prepareStudyFromStagedSource(fetchedStudy.content());
            String scope = fetchedStudy.content().getProperties().isPresent()
                    ? (fetchedStudy.content().getAssets().isPresent() ? "properties and assets" : "properties only")
                    : "assets only";
            try {
                JsonNode result = createStudyInTransaction(props, fetchedStudy, preparedStudy, signedinUser, scope);
                return new CreationResult(result, "Study created from Git: imported " + scope + ".");
            } catch (RuntimeException e) {
                throw studyImportService.translateStudyCreationConflict(e, preparedStudy.getUuid());
            }
        } catch (IOException e) {
            throw new ImportExportException("Could not read or clean up the Git study source.");
        }
    }

    /**
     * Creates the study and its source settings in a transaction.
     */
    private JsonNode createStudyInTransaction(StudySourceProperties props, FetchedStudy fetchedStudy,
                                              Study preparedStudy, User signedinUser, String scope) {
        return transactions.withTransaction(em -> {
            Study study = studyImportService.createStudyFromStagedSource(fetchedStudy.content(), preparedStudy);
            studySourceService.save(study.getId(), props);

            String uuid = study.getUuid();
            String userName = signedinUser.getName();
            String message = "Created study from Git: imported " + scope + " from " + props.getGitUrl().trim()
                    + " at commit " + fetchedStudy.commitId();
            transactions.afterCommit("Log Git study creation", () -> studyLogger.log(uuid, userName, message));

            JsonNode json = domainJsonMapper.studyAsJsonForApi(study, false, false);
            unchecked(fetchedStudy::close);
            return json;
        });
    }

    private ResolvedCredentials resolveCredentials(StudySourceProperties props, User user) {
        return transactions.withTransaction(em -> {
            User managedUser = userDao.findByUsername(user.getUsername());
            return gitCredentialsResolver.forStudyCreation(props, managedUser);
        });
    }
}
