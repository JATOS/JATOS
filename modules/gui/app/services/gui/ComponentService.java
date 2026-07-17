package services.gui;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import general.common.MessagesStrings;
import messaging.common.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.gui.ComponentProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.validation.ValidationError;
import utils.common.IOUtils;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static exceptions.common.JatosException.unchecked;

/**
 * Service class handling Components.
 */
@Singleton
public class ComponentService {

    private static final ALogger LOGGER = Logger.of(ComponentService.class);

    private final ResultRemover resultRemover;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final IOUtils ioUtils;

    @Inject
    ComponentService(ResultRemover resultRemover,
                     StudyDao studyDao,
                     ComponentDao componentDao,
                     IOUtils ioUtils) {
        this.resultRemover = resultRemover;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.ioUtils = ioUtils;
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
        clone.setComponentInput(componentToBeCloned.getComponentInput());
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
        component.setComponentInput(updatedComponent.getComponentInput());
        component.setActive(updatedComponent.isActive());
        componentDao.merge(component);
    }

    /**
     * Update a component's properties with the ones from updatedComponent
     */
    public void updateComponentAfterEdit(Component component, ComponentProperties updatedProps) {
        renameHtmlFilePath(component, updatedProps.getHtmlFilePath(), updatedProps.isHtmlFileRename());

        component.setTitle(updatedProps.getTitle());
        component.setReloadable(updatedProps.isReloadable());
        component.setActive(updatedProps.isActive());
        component.setComments(updatedProps.getComments());
        component.setComponentInput(updatedProps.getComponentInput());
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
            String clonedHtmlFilePath = ioUtils.cloneComponentHtmlFile(component.getStudy().getDirName(),
                    component.getHtmlFilePath());
            clone.setHtmlFilePath(clonedHtmlFilePath);
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
            String studyDirName = studyDao.findStudyDirNameByComponentId(component.getId());
            boolean htmlFileExists = ioUtils.checkFileInStudyAssetsDirExists(studyDirName, component.getHtmlFilePath());
            props.setHtmlFileExists(htmlFileExists);
        }
        props.setId(component.getId());
        props.setComponentInput(component.getComponentInput());
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
        study.addComponent(component);
        componentDao.persist(component);
        studyDao.merge(study);
        return component;
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
        component.setActive(props.isActive());
        component.setReloadable(props.isReloadable());
        component.setComments(props.getComments());
        component.setComponentInput(props.getComponentInput());
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
        Path currentFile = null;
        String dirName = studyDao.findStudyDirNameByComponentId(component.getId());
        String htmlFilePath = component.getHtmlFilePath();
        if (!htmlFilePath.trim().isEmpty()) {
            currentFile = unchecked(() -> ioUtils.getFileInStudyAssetsDir(dirName, htmlFilePath));
        }
        if (currentFile == null || !Files.exists(currentFile)) {
            component.setHtmlFilePath(newHtmlFilePath);
            componentDao.merge(component);
            return;
        }

        // Rename HTML file
        if (htmlFileRename) {
            unchecked(() -> ioUtils.renameHtmlFile(htmlFilePath, newHtmlFilePath, dirName));
        }
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
            LOGGER.warn(".validate: " + props.validate().stream()
                    .map(ValidationError::message)
                    .collect(Collectors.joining(", ")));
            throw new ValidationException(MessagesStrings.COMPONENT_INVALID);
        }
    }

    /**
     * Remove Component: Remove it from the given study, remove all its ComponentResults, and remove the component
     * itself.
     */
    public void remove(Component component) {
        Study study = component.getStudy();

        // Remove component's ComponentResults
        resultRemover.removeAllComponentResults(component);

        // Remove component from study
        study.removeComponent(component);
        studyDao.merge(study);

        componentDao.remove(component);
    }

    public Component getComponentFromIdOrUuid(String idOrUuid) {
        Optional<Long> componentId = StringUtils.parseLong(idOrUuid.trim());
        if (componentId.isPresent()) {
            return componentDao.findById(componentId.get());
        } else {
            return componentDao.findByUuid(idOrUuid).orElse(null);
        }
    }

}
