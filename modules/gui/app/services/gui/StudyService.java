package services.gui;

import auth.gui.AuthService;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import models.gui.StudyProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.validation.ValidationError;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for everthing Study related. Used by controllers
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyService {

    private static final ALogger LOGGER = Logger.of(StudyService.class);

    private final BatchService batchService;
    private final ComponentService componentService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final UserDao userDao;
    private final WorkerDao workerDao;
    private final IOUtils ioUtils;
    private final StudyLogger studyLogger;
    private final AuthService authService;
    private final Checker checker;

    @Inject
    StudyService(BatchService batchService, ComponentService componentService, StudyDao studyDao,
            BatchDao batchDao, UserDao userDao, WorkerDao workerDao, IOUtils ioUtils,
            StudyLogger studyLogger, AuthService authService, Checker checker) {
        this.batchService = batchService;
        this.componentService = componentService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.userDao = userDao;
        this.workerDao = workerDao;
        this.ioUtils = ioUtils;
        this.studyLogger = studyLogger;
        this.authService = authService;
        this.checker = checker;
    }

    /**
     * Clones the given Study. Does not clone id, uuid, or date. Generates a new UUID for the clone. Copies the
     * corresponding study assets. Does NOT persist the clone.
     */
    public Study clone(Study study) throws IOException {
        Study clone = new Study();
        // Generate new UUID for clone
        clone.setUuid(UUID.randomUUID().toString());
        clone.setTitle(cloneTitle(study.getTitle()));
        clone.setDescription(study.getDescription());
        clone.setDirName(study.getDirName());
        clone.setComments(study.getComments());
        clone.setEndRedirectUrl(study.getEndRedirectUrl());
        clone.setStudyEntryMsg(study.getStudyEntryMsg());
        clone.setJsonData(study.getJsonData());
        clone.setLocked(false);
        clone.setGroupStudy(study.isGroupStudy());
        clone.setLinearStudy(study.isLinearStudy());
        clone.setAllowPreview(study.isAllowPreview());

        // Clone each component
        for (Component component : study.getComponentList()) {
            Component componentClone = componentService.clone(component);
            componentClone.setStudy(clone);
            clone.addComponent(componentClone);
        }

        // Clone assets directory
        String destDirName = ioUtils.cloneStudyAssetsDirectory(study.getDirName());
        clone.setDirName(destDirName);

        return clone;
    }

    /**
     * Generates an title for the cloned study by adding '(clone)' and numbers that doesn't exist so far.
     */
    private String cloneTitle(String origTitle) {
        String cloneTitle = origTitle + " (clone)";
        int i = 2;
        while (!studyDao.findByTitle(cloneTitle).isEmpty()) {
            cloneTitle = origTitle + " (clone " + i + ")";
            i++;
        }
        return cloneTitle;
    }

    /**
     * Changes the member user in the study. Additionally changes the user's worker in all of the study's batches.
     * Persisting.
     */
    public void changeUserMember(Study study, User userToChange, boolean isMember) throws ForbiddenException {
        Set<User> userList = study.getUserList();
        if (isMember) {
            if (userList.contains(userToChange)) {
                return;
            }
            study.addUser(userToChange);
            study.getBatchList().forEach(b -> b.addWorker(userToChange.getWorker()));
        } else {
            if (!userList.contains(userToChange)) {
                return;
            }
            if (userList.size() <= 1) {
                throw new ForbiddenException(MessagesStrings.STUDY_AT_LEAST_ONE_USER);
            }
            study.removeUser(userToChange);
            study.getBatchList().forEach(b -> b.removeWorker(userToChange.getWorker()));
        }
        studyDao.update(study);
        userDao.update(userToChange);
    }

    /**
     * Adds all users as members to the given study. Additionally adds all user's Jatos workers to the study's batches.
     */
    public void addAllUserMembers(Study study) {
        List<User> userList = userDao.findAll();
        study.getUserList().addAll(userList);
        List<Worker> usersWorkerList = userList.stream().map(User::getWorker).collect(Collectors.toList());
        study.getBatchList().forEach(b -> b.addAllWorkers(usersWorkerList));

        studyDao.update(study);
        userList.forEach(userDao::update);
    }

    /**
     * Removes all member users from the given study except the signed-in user. Additionally, removes all user's Jatos
     * workers from the study's batches (except the signed-in user's workers).
     */
    public void removeAllUserMembers(Study study) {
        List<User> userList = userDao.findAll();
        userList.remove(authService.getSignedinUser());
        List<Worker> usersWorkerList = userList.stream().map(User::getWorker).collect(Collectors.toList());
        study.getBatchList().forEach(b -> b.removeAllWorkers(usersWorkerList));
        study.getUserList().removeAll(userList);
        studyDao.update(study);
        userList.forEach(userDao::update);
    }

    /**
     * Changes the position of the given component within the given study to the new position given in newPosition.
     * Remember the first position is 1 (and not 0). Throws BadRequestException if number has wrong format or number
     * isn't within the studies positions.
     */
    public void changeComponentPosition(String newPosition, Study study, Component component)
            throws BadRequestException {
        try {
            int currentIndex = study.getComponentList().indexOf(component);
            int newIndex = Integer.parseInt(newPosition) - 1;
            study.getComponentList().remove(currentIndex);
            study.getComponentList().add(newIndex, component);
            studyDao.update(study);
        } catch (NumberFormatException e) {
            throw new BadRequestException(MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
        } catch (IndexOutOfBoundsException e) {
            throw new BadRequestException(
                    MessagesStrings.studyReorderUnknownPosition(newPosition, study.getId()));
        }
    }

    /**
     * Binds study properties from a edit/create study request onto a Study.
     */
    private Study bindToStudy(StudyProperties studyProperties) {
        Study study = new Study();
        bindToStudyWithoutDirName(study, studyProperties);
        study.setDirName(studyProperties.getDirName());
        return study;
    }

    /**
     * Create and persist a Study with given properties. Creates and persists the default Batch. If the study has
     * components already, it persists them too. Adds the given user to the users of this study.
     */
    public Study createAndPersistStudy(User signedinUser, StudyProperties studyProperties) {
        Study study = bindToStudy(studyProperties);
        study.setUuid(studyProperties.getUuid());
        return createAndPersistStudy(signedinUser, study);
    }

    /**
     * Persists the given Study. Creates and persists the default Batch. If the study has components already it persists
     * them too. Adds the given user to the users of this study.
     */
    public Study createAndPersistStudy(User signedinUser, Study study) {
        if (study.getUuid() == null) {
            study.setUuid(UUID.randomUUID().toString());
        }
        study.getComponentList().forEach(c -> componentService.createComponent(study, c));

        studyDao.create(study);

        if (study.getBatchList().isEmpty()) {
            // Create default batch if we have no batch
            Batch defaultBatch = batchService.createDefaultBatch(study);
            study.addBatch(defaultBatch);
            batchDao.create(defaultBatch);
        } else {
            study.getBatchList().forEach(b -> batchService.createBatch(b, study));
            study.getBatchList().forEach(batchDao::create);
        }

        // Add user
        addUserToStudy(study, signedinUser);

        studyDao.update(study);
        studyLogger.create(study);
        studyLogger.log(study, signedinUser, "Created study");
        if (!Strings.isNullOrEmpty(study.getDescription())) {
            studyLogger.logStudyDescriptionHash(study, signedinUser);
        }
        return study;
    }

    private void addUserToStudy(Study study, User user) {
        study.addUser(user);
        user.addStudy(study);
        studyDao.update(study);
        userDao.update(user);

        // For each of the study's batches add the user's JatosWorker
        JatosWorker jatosWorker = user.getWorker();
        for (Batch batch : study.getBatchList()) {
            batch.addWorker(jatosWorker);
            batchDao.update(batch);
        }
        workerDao.update(jatosWorker);
    }

    /**
     * Update properties of study with properties of updatedStudy.
     */
    public void updateStudy(Study study, Study updatedStudy, User signedinUser) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescriptionHash(),
                updatedStudy.getDescriptionHash());
        updateStudyCommon(study, updatedStudy);
        study.setDirName(updatedStudy.getDirName());
        studyDao.update(study);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    /**
     * Update properties of study with properties of updatedStudy but not Study's field dirName.
     */
    public void updateStudyWithoutDirName(Study study, Study updatedStudy, User signedinUser) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescriptionHash(),
                updatedStudy.getDescriptionHash());
        updateStudyCommon(study, updatedStudy);
        studyDao.update(study);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    private void updateStudyCommon(Study study, Study updatedStudy) {
        study.setTitle(updatedStudy.getTitle());
        study.setDescription(updatedStudy.getDescription());
        study.setComments(updatedStudy.getComments());
        study.setStudyEntryMsg(updatedStudy.getStudyEntryMsg());
        study.setEndRedirectUrl(updatedStudy.getEndRedirectUrl());
        study.setJsonData(updatedStudy.getJsonData());
        study.setLinearStudy(updatedStudy.isLinearStudy());
        study.setAllowPreview(updatedStudy.isAllowPreview());
        study.setGroupStudy(updatedStudy.isGroupStudy());
    }

    /**
     * Update Study with given properties and persist. It doesn't update Study's dirName field.
     */
    public void updateStudy(Study study, StudyProperties studyProperties, User signedinUser) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescription(), studyProperties.getDescription());
        bindToStudyWithoutDirName(study, studyProperties);
        studyDao.update(study);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    /**
     * Update Study's description and store new description hash in the study log
     */
    public void updateDescription(Study study, String description, User signedinUser) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescription(), description);
        study.setDescription(description);
        studyDao.update(study);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    /**
     * Update properties of study with properties of updatedStudy (excluding study's dir name). Does not persist.
     */
    public void bindToStudyWithoutDirName(Study study, StudyProperties studyProperties) {
        study.setTitle(studyProperties.getTitle());
        study.setDescription(studyProperties.getDescription());
        study.setComments(studyProperties.getComments());
        study.setStudyEntryMsg(studyProperties.getStudyEntryMsg());
        study.setEndRedirectUrl(studyProperties.getEndRedirectUrl());
        study.setJsonData(studyProperties.getJsonData());
        study.setGroupStudy(studyProperties.isGroupStudy());
        study.setLinearStudy(studyProperties.isLinearStudy());
        study.setAllowPreview(studyProperties.isAllowPreview());
    }

    /**
     * Renames the directory in the file system and persists the study's property.
     */
    public void renameStudyAssetsDir(Study study, String newDirName, boolean dirRename) throws IOException {
        if (dirRename) {
            ioUtils.renameStudyAssetsDir(study.getDirName(), newDirName);
        } else {
            if (!ioUtils.checkStudyAssetsDirExists(newDirName)) {
                throw new IOException("Study asset directory " + newDirName + " does not exist.");
            }
        }
        study.setDirName(newDirName);
        studyDao.update(study);
    }

    /**
     * Fills a new StudyProperties with values from the given Study.
     */
    public StudyProperties bindToProperties(Study study) {
        StudyProperties studyProperties = new StudyProperties();
        studyProperties.setStudyId(study.getId());
        studyProperties.setUuid(study.getUuid());
        studyProperties.setTitle(study.getTitle());
        studyProperties.setDescription(study.getDescription());
        studyProperties.setDate(study.getDate());
        studyProperties.setLocked(study.isLocked());
        studyProperties.setGroupStudy(study.isGroupStudy());
        studyProperties.setLinearStudy(study.isLinearStudy());
        studyProperties.setAllowPreview(study.isAllowPreview());
        studyProperties.setDirName(study.getDirName());
        studyProperties.setComments(study.getComments());
        studyProperties.setEndRedirectUrl(study.getEndRedirectUrl());
        studyProperties.setStudyEntryMsg(study.getStudyEntryMsg());
        studyProperties.setJsonData(study.getJsonData());
        return studyProperties;
    }

    /**
     * Validates the study by converting it to StudyProperties and uses its validate method. Throws ValidationException
     * in case of an error.
     */
    public void validate(Study study) throws ValidationException {
        StudyProperties studyProperties = bindToProperties(study);
        if (studyProperties.validate() != null) {
            LOGGER.warn(".validate: " + studyProperties.validate().stream().map(ValidationError::message)
                    .collect(Collectors.joining(", ")));
            throw new ValidationException("Study is invalid");
        }
    }

    /**
     * Removes the given study, its components, component results, study results, group results and batches and persists
     * the changes to the database. It also deletes the study's assets from the disk.
     */
    public void removeStudyInclAssets(Study study, User signedinUser) throws IOException {
        // Remove all study's batches and their StudyResults and GroupResults
        for (Batch batch : Lists.newArrayList(study.getBatchList())) {
            batchService.remove(batch, signedinUser);
        }

        // Remove this study from all member users
        for (User user : study.getUserList()) {
            user.removeStudy(study);
            userDao.update(user);
        }

        // Remove study. This also removes all study's components and their ComponentResults via cascading.
        studyDao.remove(study);

        ioUtils.removeStudyAssetsDir(study.getDirName());
        studyLogger.log(study, signedinUser, "Removed study");
        studyLogger.retire(study);
    }

    public Study getStudyFromIdOrUuid(String id) throws NotFoundException, ForbiddenException {
        Optional<Long> studyId = Helpers.parseLong(id.trim());
        User signedinUser = authService.getSignedinUser();
        Study study;
        if (studyId.isPresent()) {
            study = studyDao.findById(studyId.get());
            if (study == null) throw new NotFoundException("Couldn't find study with ID " + studyId.get());
            checker.checkStandardForStudy(study, studyId.get(), signedinUser);
        } else {
            study = studyDao.findByUuid(id)
                    .orElseThrow(() -> new NotFoundException("Couldn't find study with UUID " + id));
            checker.checkStandardForStudy(study, study.getId(), signedinUser);
        }
        return study;
    }

}
