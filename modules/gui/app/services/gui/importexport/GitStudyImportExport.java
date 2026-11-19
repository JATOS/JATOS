package services.gui.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.UserDao;
import exceptions.common.ImportExportException;
import general.common.JpaTransactions;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.Study;
import models.common.User;
import models.gui.StudySourceProperties;
import services.gui.importexport.GitCredentialsResolver.ResolvedCredentials;
import services.gui.importexport.GitStudySourceIO.FetchedStudy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static exceptions.common.JatosException.unchecked;

/**
 * Creates studies from Git snapshots. Network access finishes before the creation transaction starts.
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

    @Inject
    public GitStudyImportExport(JpaTransactions transactions,
                                GitCredentialsResolver gitCredentialsResolver,
                                GitStudySourceIO gitStudySourceIO,
                                StudyImportService studyImportService,
                                StudySourceService studySourceService,
                                DomainJsonMapper domainJsonMapper,
                                StudyLogger studyLogger,
                                UserDao userDao) {
        this.transactions = transactions;
        this.gitCredentialsResolver = gitCredentialsResolver;
        this.gitStudySourceIO = gitStudySourceIO;
        this.studyImportService = studyImportService;
        this.studySourceService = studySourceService;
        this.domainJsonMapper = domainJsonMapper;
        this.studyLogger = studyLogger;
        this.userDao = userDao;
    }

    public record CreationResult(JsonNode study, String message) {}

    /**
     * Caller authorizes creation and validates source properties; existing UUIDs are never overwritten.
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
     * Creates the study and source, schedules logging, and cleans up staging before the caller commits.
     */
    private JsonNode createStudyInTransaction(StudySourceProperties props, FetchedStudy fetchedStudy,
                                              Study preparedStudy, User signedinUser, String scope) {
        return transactions.withTransaction(em -> {
            Study study = studyImportService.createStudyFromStagedSource(fetchedStudy.content(), preparedStudy);
            studySourceService.save(study.getId(), props);

            String uuid = study.getUuid();
            String userName = signedinUser.getName();
            String message = "Created study from Git: imported " + scope + " from " + props.getGitUrl().trim()
                    + " at commit " + fetchedStudy.commit();
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
