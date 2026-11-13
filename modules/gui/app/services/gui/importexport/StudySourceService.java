package services.gui.importexport;

import com.google.common.base.Strings;
import daos.common.StudyDao;
import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import general.common.Common;
import general.common.JpaTransactions;
import general.common.StudyLogger;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.Study;
import models.common.StudySource;
import models.gui.StudySourceProperties;
import utils.common.IOUtils;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Objects;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static general.common.ApiEnvelope.ErrorCode.CONFIG_ERROR;

/**
 * Handles reading, validating, and saving a study’s source configuration. Saving does not fetch or import source
 * content.
 */
@Singleton
public class StudySourceService {

    private final JpaTransactions transactions;
    private final StudyDao studies;
    private final GitCredentialsService credentials;
    private final StudyLogger logger;
    private final IOUtils ioUtils;

    @Inject
    public StudySourceService(JpaTransactions transactions,
                              StudyDao studies,
                              GitCredentialsService credentials,
                              StudyLogger logger,
                              IOUtils ioUtils) {
        this.transactions = transactions;
        this.studies = studies;
        this.credentials = credentials;
        this.logger = logger;
        this.ioUtils = ioUtils;
    }

    public StudySourceProperties get(long studyId) {
        Study study = studies.findByIdWithSourceAndLock(studyId);
        StudySource source = study.getStudySource();
        // Reading an unconfigured study must not create a persisted source.
        if (source == null) source = new StudySource(study);
        return bindToProperties(source);
    }

    /**
     * Validates and saves source settings and credentials for an owner or enabled superuser, preserving sync history.
     */
    public StudySourceProperties save(long studyId, StudySourceProperties props) {
        Study study = studies.findByIdWithSourceAndLock(studyId);

        var errors = props.validate();
        if (errors != null)
            throw new FieldValidationException(errors.getFirst().key(), errors.getFirst().message());

        StudySource source = study.getStudySource();
        if (source == null) source = new StudySource(study);

        boolean replaceCredentials = !Strings.isNullOrEmpty(props.getSecret());
        boolean updateUsername = source.getGitCredentials() != null
                && !Strings.isNullOrEmpty(props.getUsername())
                && !Objects.equals(props.getUsername(), source.getGitCredentials().getUsername());
        StudySourceProperties propsBefore = bindToProperties(source);
        String localFolder = props.getType() == StudySource.Type.LOCAL_FOLDER
                ? validateFolder(props.getLocalFolderPath()) : null;

        // Do not allow using the same credentials for different git origins, e.g., change from GitHub to GitLab
        String gitOrigin = props.getType() == StudySource.Type.GIT
                ? HttpUtils.getGitOriginFromRepositoryUrl(props.getGitUrl()) : null;
        if (props.getType() == StudySource.Type.GIT
                && !props.isRemoveCredentials() && !replaceCredentials
                && source.getGitCredentials() != null
                && !Objects.equals(source.getGitCredentials().getOrigin(), gitOrigin)) {
            throw new FieldValidationException("gitUrl",
                    "Remove or replace the saved credentials when changing the repository origin.");
        }

        bindToSource(source, props, localFolder);
        // Attach the source; Hibernate saves it through Study's cascade mapping.
        study.setStudySource(source);

        if (props.getType() != StudySource.Type.GIT || props.isRemoveCredentials()) {
            if (source.getGitCredentials() != null) credentials.removeGitCredentialsFromSource(studyId);
        } else if (replaceCredentials || updateUsername) {
            credentials.saveGitCredentialsForSource(studyId, props.getUsername(),
                    HttpUtils.getGitOriginFromRepositoryUrl(source.getGitUrl()), props.getSecret());
        }
        StudySourceProperties propsAfter = bindToProperties(source);
        if (!isEqualStudySourceProperties(propsBefore, propsAfter)
                || props.getType() == StudySource.Type.GIT
                && replaceCredentials) {
            String message = "Changed study source configuration to " + source.getType();
            String studyUuid = study.getUuid();
            String userName = Context.current().args().get(SIGNEDIN_USER).getName();
            transactions.afterCommit("Log study source configuration change",
                    () -> logger.log(studyUuid, userName, message));
        }
        return propsAfter;
    }

    /**
     * Copies validated settings and clears inactive fields without changing sync history.
     */
    private void bindToSource(StudySource source, StudySourceProperties props, String localFolder) {
        source.setType(props.getType());
        source.setGitUrl(props.getType() == StudySource.Type.GIT ? props.getGitUrl().trim() : null);
        source.setGitRef(props.getType() == StudySource.Type.GIT ? StringUtils.trimIfNotNull(props.getGitRef()) : null);
        source.setLocalFolderPath(localFolder);
        source.setAnonymous(props.getType() == StudySource.Type.GIT && props.isAnonymous());
    }

    private StudySourceProperties bindToProperties(StudySource source) {
        StudySourceProperties props = new StudySourceProperties();
        props.setType(source.getType());
        props.setGitUrl(source.getGitUrl());
        props.setGitRef(source.getGitRef());
        props.setLocalFolderPath(source.getLocalFolderPath());
        props.setAnonymous(source.isAnonymous());
        props.setCredentialsConfigured(source.getGitCredentials() != null);
        props.setUsername(source.getGitCredentials() == null ? null : source.getGitCredentials().getUsername());
        props.setLocalFolderAllowed(localFolderAllowed());
        return props;
    }

    /**
     * Compare stored configuration, including the username; secrets and installation flags are excluded.
     */
    private boolean isEqualStudySourceProperties(StudySourceProperties before, StudySourceProperties after) {
        return before.getType() == after.getType()
                && Objects.equals(before.getGitUrl(), after.getGitUrl())
                && Objects.equals(before.getGitRef(), after.getGitRef())
                && Objects.equals(before.getUsername(), after.getUsername())
                && Objects.equals(before.getLocalFolderPath(), after.getLocalFolderPath())
                && before.isAnonymous() == after.isAnonymous()
                && before.isCredentialsConfigured() == after.isCredentialsConfigured();
    }

    private boolean localFolderAllowed() {
        return !Common.isServer() || !Common.getStudySourcesAllowedRoots().isEmpty();
    }

    private String validateFolder(String value) {
        if (!localFolderAllowed()) throw new BadRequestException(
                "Local-folder sources require configured allowed roots in server mode.", CONFIG_ERROR);
        try {
            var path = ioUtils.resolveReadableDirectoryWithinRoots(value, Common.getStudySourcesAllowedRoots());
            if (path.isPresent()) return path.get().toString();
        } catch (IOException | IllegalArgumentException e) {
            throw new FieldValidationException("localFolderPath",
                    "The source folder or its allowed root is unavailable or unreadable.");
        }
        throw new FieldValidationException("localFolderPath",
                "The source folder must be inside one of the configured allowed roots");
    }
}
