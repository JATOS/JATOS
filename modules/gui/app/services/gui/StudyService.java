package services.gui;

import general.common.Http.Context;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
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
import play.db.jpa.JPAApi;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.util.*;
import java.util.stream.Collectors;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class for everything Study related.
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyService {

    private static final ALogger LOGGER = Logger.of(StudyService.class);

    private final JPAApi jpa;
    private final BatchService batchService;
    private final ComponentService componentService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final UserDao userDao;
    private final WorkerDao workerDao;
    private final IOUtils ioUtils;
    private final StudyLogger studyLogger;
    private final Checker checker;

    @Inject
    StudyService(JPAApi jpa,
                 BatchService batchService,
                 ComponentService componentService,
                 StudyDao studyDao,
                 BatchDao batchDao,
                 UserDao userDao,
                 WorkerDao workerDao,
                 IOUtils ioUtils,
                 StudyLogger studyLogger,
                 Checker checker) {
        this.jpa = jpa;
        this.batchService = batchService;
        this.componentService = componentService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.userDao = userDao;
        this.workerDao = workerDao;
        this.ioUtils = ioUtils;
        this.studyLogger = studyLogger;
        this.checker = checker;
    }

    /**
     * Clones the given Study. Does not clone id, uuid, or date. Generates a new UUID for the clone. Copies the
     * corresponding study assets. Does NOT persist the clone.
     */
    public Study clone(Study study) {
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
     * Generates a title for the cloned study by adding '(clone)' and numbers that doesn't exist so far.
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
     * Changes the member user in the study. Additionally, changes the user's worker in all the study's batches.
     * Persisting.
     */
    public void changeUserMember(Study study, User userToChange, boolean isMember) {
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
        jpa.withTransaction(em -> {
            studyDao.merge(study);
            userDao.merge(userToChange);
        });
    }

    /**
     * Adds all users as members to the given study. Additionally, adds all user's Jatos workers to the study's
     * batches.
     */
    public void addAllUserMembers(Study study) {
        List<User> userList = userDao.findAll();
        study.getUserList().addAll(userList);
        List<Worker> usersWorkerList = userList.stream().map(User::getWorker).collect(Collectors.toList());
        study.getBatchList().forEach(b -> b.addAllWorkers(usersWorkerList));

        jpa.withTransaction(em -> {
            studyDao.merge(study);
            userList.forEach(userDao::merge);
        });
    }

    /**
     * Removes all member users from the given study except the signed-in user. Additionally, removes all user's Jatos
     * workers from the study's batches (except the signed-in user's workers).
     */
    public void removeAllUserMembers(Study study) {
        List<User> userList = userDao.findAll();
        userList.remove(Context.current().args().get(SIGNEDIN_USER));
        List<Worker> usersWorkerList = userList.stream().map(User::getWorker).collect(Collectors.toList());
        study.getBatchList().forEach(b -> b.removeAllWorkers(usersWorkerList));
        userList.forEach(study.getUserList()::remove);

        jpa.withTransaction(em -> {
            studyDao.merge(study);
            userList.forEach(userDao::merge);
        });
    }

    /**
     * Changes the position of the given component within the given study to the new position given in the newPosition.
     * Remember the first position is 1 (and not 0). Throws BadRequestException if the number has a wrong format or
     * number isn't within the study's positions.
     */
    public void changeComponentPosition(String newPosition, Study study, Component component) {
        try {
            int currentIndex = study.getComponentList().indexOf(component);
            int newIndex = Integer.parseInt(newPosition) - 1;
            study.getComponentList().remove(currentIndex);
            study.getComponentList().add(newIndex, component);
            studyDao.merge(study);
        } catch (NumberFormatException e) {
            throw new BadRequestException(MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
        } catch (IndexOutOfBoundsException e) {
            throw new BadRequestException(MessagesStrings.studyReorderUnknownPosition(newPosition, study.getId()));
        }
    }

    /**
     * Create and persist a Study with given properties. Creates and persists the default Batch. If the study has
     * components already, it persists them too. Adds the given user to the users of this study.
     */
    public Study createAndPersistStudy(StudyProperties studyProperties) {
        Study study = new Study();
        bindToStudyWithoutDirName(study, studyProperties);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return createAndPersistStudy(signedinUser, study);
    }

    /**
     * Persists the given Study. Creates and persists the default Batch. If the study has components already, it
     * persists them too. Adds the given user to the users of this study.
     */
    public Study createAndPersistStudy(User user, Study study) {
        return jpa.withTransaction(em -> {
            User managedUser = em.merge(user);
            Study managedStudy = em.merge(study);

            managedStudy.getComponentList().forEach(c -> c.setStudy(managedStudy));

            studyDao.persist(managedStudy);

            if (managedStudy.getBatchList().isEmpty()) {
                // Create a default batch if we have no batch
                Batch defaultBatch = batchService.createDefaultBatch(managedStudy);
                managedStudy.addBatch(defaultBatch);
                batchDao.persist(defaultBatch);
            } else {
                managedStudy.getBatchList().forEach(b -> batchService.createBatch(b, managedStudy));
                managedStudy.getBatchList().forEach(batchDao::persist);
            }

            // Add user
            addUserToStudy(managedStudy, managedUser);

            studyDao.merge(managedStudy);
            studyLogger.create(managedStudy);
            studyLogger.log(managedStudy, managedUser, "Created study");
            if (!Strings.isNullOrEmpty(managedStudy.getDescription())) {
                studyLogger.logStudyDescriptionHash(managedStudy, managedUser);
            }
            return managedStudy;
        });
    }

    private void addUserToStudy(Study study, User user) {
        study.addUser(user);
        user.addStudy(study);

        studyDao.merge(study);
        userDao.merge(user);

        // For each of the study's batches add the user's JatosWorker
        JatosWorker jatosWorker = user.getWorker();
        List<Batch> batchList = study.getBatchList();
        for (Batch batch : batchList) {
            batch.addWorker(jatosWorker);
            batchDao.merge(batch);
        }
        workerDao.merge(jatosWorker);
    }

    /**
     * Update properties of the study with properties of the updatedStudy.
     */
    public void updateStudy(Study study, Study updatedStudy) {
        jpa.withTransaction(em -> {
            boolean logStudyDescriptionHash = !Objects.equals(study.getDescriptionHash(), updatedStudy.getDescriptionHash());
            updateStudyCommon(study, updatedStudy);
            study.setDirName(updatedStudy.getDirName());
            studyDao.merge(study);
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
        });
    }

    /**
     * Update properties of the study with properties of updatedStudy but not Study's field dirName.
     */
    public void updateStudyWithoutDirName(Study study, Study updatedStudy) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescriptionHash(), updatedStudy.getDescriptionHash());
        jpa.withTransaction(em -> {
            updateStudyCommon(study, updatedStudy);
            studyDao.merge(study);
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
        });
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
    public void updateStudy(Study study, StudyProperties studyProperties) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescription(), studyProperties.getDescription());
        bindToStudyWithoutDirName(study, studyProperties);
        studyDao.merge(study);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    /**
     * Update Study's description and store a new description hash in the study log
     */
    public void updateDescription(Study study, String description) {
        boolean logStudyDescriptionHash = !Objects.equals(study.getDescription(), description);
        study.setDescription(description);
        studyDao.merge(study);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (logStudyDescriptionHash) studyLogger.logStudyDescriptionHash(study, signedinUser);
    }

    /**
     * Update properties of the study with properties of the updatedStudy (excluding study's dir name). Does not
     * persist.
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
    public void renameStudyAssetsDir(Study study, String newDirName) {
        ioUtils.renameStudyAssetsDir(study.getDirName(), newDirName);
        study.setDirName(newDirName);
        studyDao.merge(study);
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
    public void validate(Study study) {
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
    public void removeStudyInclAssets(Study study) {
        jpa.withTransaction(em -> {
            // Remove all study's batches and their StudyResults and GroupResults
            for (Batch batch : Lists.newArrayList(study.getBatchList())) {
                batchService.remove(batch);
            }

            // Remove this study from all member users
            for (User user : study.getUserList()) {
                user.removeStudy(study);
                userDao.merge(user);
            }

            // Remove study. This also removes all study's components and their ComponentResults via cascading.
            studyDao.remove(study);

            ioUtils.removeStudyAssetsDir(study.getDirName());

            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            studyLogger.log(study, signedinUser, "Removed study");
            studyLogger.retire(study);
        });
    }

    public Study getStudyFromIdOrUuid(String idOrUuid) {
        Optional<Long> studyId = Helpers.parseLong(idOrUuid.trim());
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study;
        if (studyId.isPresent()) {
            study = studyDao.findById(studyId.get());
            if (study == null) throw new NotFoundException("Couldn't find study with ID " + idOrUuid);
            checker.checkStandardForStudy(study, studyId.get(), signedinUser);
        } else {
            study = studyDao.findByUuid(idOrUuid)
                    .orElseThrow(() -> new NotFoundException("Couldn't find study with UUID " + idOrUuid));
            checker.checkStandardForStudy(study, study.getId(), signedinUser);
        }
        return study;
    }

}
