package services.gui;

import general.common.Http.Context;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.common.IOException;
import exceptions.common.NotFoundException;
import general.common.MessagesStrings;
import messaging.common.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.ComponentProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.validation.ValidationError;
import play.db.jpa.JPAApi;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class for JATOS Controllers (not Publix) handling Component entities.
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentService {

    private static final ALogger LOGGER = Logger.of(ComponentService.class);

    private final JPAApi jpa;
    private final ResultRemover resultRemover;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final IOUtils ioUtils;
    private final Checker checker;

    @Inject
    ComponentService(JPAApi jpa,
                     ResultRemover resultRemover,
                     StudyDao studyDao,
                     ComponentDao componentDao,
                     IOUtils ioUtils,
                     Checker checker) {
        this.jpa = jpa;
        this.resultRemover = resultRemover;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.ioUtils = ioUtils;
        this.checker = checker;
    }

    /**
     * Clones a Component entity. Does not clone id, uuid, or date. Does not persist the clone. Does not clone the HTML
     * file.
     */
    public Component clone(Component componentToBeCloned) {
        Component clone = new Component();
        clone.setStudy(componentToBeCloned.getStudy());
        // Generate new UUID for clone
        clone.setUuid(UUID.randomUUID().toString());
        clone.setTitle(componentToBeCloned.getTitle());
        clone.setHtmlFilePath(componentToBeCloned.getHtmlFilePath());
        clone.setReloadable(componentToBeCloned.isReloadable());
        clone.setActive(componentToBeCloned.isActive());
        clone.setJsonData(componentToBeCloned.getJsonData());
        clone.setComments(componentToBeCloned.getComments());
        return clone;
    }

    /**
     * Generates an title for the cloned study that doesn't exist so far
     */
    private String cloneTitle(String origTitle) {
        String cloneTitle = origTitle + " (clone)";
        int i = 2;
        while (!componentDao.findByTitle(cloneTitle).isEmpty()) {
            cloneTitle = origTitle + " (clone " + i + ")";
            i++;
        }
        return cloneTitle;
    }

    /**
     * Update a component's properties with the ones from updatedComponent.
     */
    void updateProperties(Component component, Component updatedComponent) {
        component.setTitle(updatedComponent.getTitle());
        component.setReloadable(updatedComponent.isReloadable());
        component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
        component.setComments(updatedComponent.getComments());
        component.setJsonData(updatedComponent.getJsonData());
        component.setActive(updatedComponent.isActive());
        componentDao.merge(component);
    }

    /**
     * Update a component's properties with the ones from updatedComponent, but not htmlFilePath and not active.
     */
    public void updateComponentAfterEdit(Component component, ComponentProperties updatedProps) {
        component.setTitle(updatedProps.getTitle());
        component.setReloadable(updatedProps.isReloadable());
        component.setComments(updatedProps.getComments());
        component.setJsonData(updatedProps.getJsonData());
        componentDao.merge(component);
    }

    /**
     * Does the same as {@link #clone(Component) cloneComponent} and additionally clones the HTML file and changes the
     * title.
     */
    public Component cloneWholeComponent(Component component) {
        Component clone = clone(component);
        clone.setTitle(cloneTitle(component.getTitle()));
        try {
            String clonedHtmlFileName = ioUtils.cloneComponentHtmlFile(component.getStudy().getDirName(),
                    component.getHtmlFilePath());
            clone.setHtmlFilePath(clonedHtmlFileName);
        } catch (IOException e) {
            // Just log it and give a warning - a component is allowed to have no HTML file
            RequestScopeMessaging.warning(MessagesStrings.componentCloneHtmlNotCloned(component.getHtmlFilePath()));
            LOGGER.info(".cloneWholeComponent: " + e.getMessage());
        }
        return clone;
    }

    public ComponentProperties bindToProperties(Component component) {
        ComponentProperties props = new ComponentProperties();
        props.setUuid(component.getUuid());
        props.setTitle(component.getTitle());
        props.setActive(component.isActive());
        props.setComments(component.getComments());
        props.setDate(component.getDate());
        props.setHtmlFilePath(component.getHtmlFilePath());
        if (component.getStudy() != null) {
            props.setHtmlFileExists(ioUtils.checkFileInStudyAssetsDirExists(component.getStudy().getDirName(),
                    component.getHtmlFilePath()));
        }
        props.setId(component.getId());
        props.setJsonData(component.getJsonData());
        props.setReloadable(component.isReloadable());
        if (component.getStudy() != null) {
            props.setStudyId(component.getStudy().getId());
        }
        return props;
    }

    /**
     * Initialise and persist the given Component. Updates its study.
     */
    public Component createAndPersistComponent(Study study, Component component) {
        return jpa.withTransaction(em -> {
            component.setStudy(study);
            if (!study.hasComponent(component)) {
                study.addComponent(component);
            }
            componentDao.persist(component);
            studyDao.merge(study);
            return component;
        });
    }

    /**
     * Create and persist a Component with given properties. Updates its study.
     */
    public Component createAndPersistComponent(Study study, ComponentProperties componentProperties) {
        Component component = bindToComponent(componentProperties);
        return createAndPersistComponent(study, component);
    }

    /**
     * Binds component data from an edit/create component request onto a Component. Play's default form binder doesn't
     * work here.
     */
    private Component bindToComponent(ComponentProperties props) {
        Component component = new Component();
        component.setTitle(props.getTitle());
        component.setHtmlFilePath(props.getHtmlFilePath());
        component.setReloadable(props.isReloadable());
        component.setComments(props.getComments());
        component.setJsonData(props.getJsonData());
        return component;
    }

    /**
     * Renames the path to the HTML file in the file system and persists the component's property.
     */
    public void renameHtmlFilePath(Component component, String newHtmlFilePath, boolean htmlFileRename) {

        // If the new HTML file name is empty, persist an empty string
        if (newHtmlFilePath == null || newHtmlFilePath.trim().isEmpty()) {
            component.setHtmlFilePath("");
            componentDao.merge(component);
            return;
        }

        // What if the current HTML file doesn't exist
        File currentFile = null;
        if (!component.getHtmlFilePath().trim().isEmpty()) {
            currentFile = ioUtils.getFileInStudyAssetsDir(component.getStudy().getDirName(),
                    component.getHtmlFilePath());
        }
        if (currentFile == null || !currentFile.exists()) {
            component.setHtmlFilePath(newHtmlFilePath);
            componentDao.merge(component);
            return;
        }

        // Rename HTML file
        if (htmlFileRename) ioUtils.renameHtmlFile(component.getHtmlFilePath(), newHtmlFilePath,
                component.getStudy().getDirName());
        component.setHtmlFilePath(newHtmlFilePath);
        componentDao.merge(component);
    }

    /**
     * Validates the component by using the Component's model validation method. Throws ValidationException in case of
     * an error.
     */
    public void validate(Component component) {
        ComponentProperties props = bindToProperties(component);
        if (props.validate() != null) {
            LOGGER.warn(".validate: " + props.validate().stream().map(ValidationError::message)
                    .collect(Collectors.joining(", ")));
            throw new ValidationException(MessagesStrings.COMPONENT_INVALID);
        }
    }

    /**
     * Remove Component: Remove it from the given study, remove all its ComponentResults, and remove the component
     * itself.
     */
    public void remove(Component component) {
        jpa.withTransaction(entityManager -> {
            Component managedComponent = componentDao.merge(component);
            Study study = managedComponent.getStudy();
            // Remove component from the study
            study.removeComponent(managedComponent);
            studyDao.merge(study);
            // Remove component's ComponentResults
            resultRemover.removeAllComponentResults(managedComponent);
            componentDao.remove(managedComponent);
        });
    }

    public Component getComponentFromIdOrUuid(String idOrUuid) {
        Optional<Long> componentId = Helpers.parseLong(idOrUuid.trim());
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component;
        if (componentId.isPresent()) {
            component = componentDao.findById(componentId.get());
            if (component == null) throw new NotFoundException("Couldn't find component with ID " + idOrUuid);
            checker.checkStandardForComponent(componentId.get(), component, signedinUser);
        } else {
            component = componentDao.findByUuid(idOrUuid)
                    .orElseThrow(() -> new NotFoundException("Couldn't find component with UUID " + idOrUuid));
            checker.checkStandardForComponent(component.getId(), component, signedinUser);
        }
        return component;
    }

}
