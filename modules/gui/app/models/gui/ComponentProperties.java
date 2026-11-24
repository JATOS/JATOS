package models.gui;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;
import json.common.DefaultJson;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * DTO of component properties for UI. The corresponding database entity is {@link models.common.Component}.
 */
@Constraints.Validate
public class ComponentProperties implements Validatable<List<ValidationError>> {

    public static final String ID = "id";
    public static final String UUID = "uuid";
    public static final String TITLE = "title";
    public static final String HTML_FILE_PATH = "htmlFilePath";
    public static final String COMPONENT_INPUT = "componentInput";
    public static final String COMMENTS = "comments";

    @JsonProperty(access = READ_ONLY)
    private Long id;

    /**
     * Universally, (worldwide) unique ID. Used for import/export between different JATOS instances. A study can have
     * only one component with the same UUID, although it is allowed to have other studies that have this component with
     * this UUID.
     */
    @JsonProperty(access = READ_ONLY)
    private String uuid;

    @JsonProperty(access = READ_ONLY)
    private Long studyId;

    private String title;

    /**
     * Timestamp of the creation or the last update of this component
     */
    @JsonProperty(access = READ_ONLY)
    private Timestamp date;

    /**
     * Local path to component's HTML file in the study assets' dir. File separators are persisted as '/'.
     */
    private String htmlFilePath;

    /**
     * Should the actual HTML file on the disk be renamed - or just the value in the DB?
     */
    @JsonProperty(access = WRITE_ONLY)
    private boolean htmlFileRename = false;

    /**
     * Does the html file exists in the file system?
     */
    private boolean htmlFileExists = false;

    /**
     * Is the webpage of this component reloadable in the browser?
     */
    private boolean reloadable = false;

    /**
     * An inactive component can't be used within a study - it generates an error message if one try. Further it's
     * skipped if one uses startNextComponent from the public API.
     */
    private boolean active = true;

    /**
     * User comments, reminders, something to share with others. They have no further meaning.
     */
    private String comments;

    /**
     * Data in JSON format that is injected into jatos.js as 'jatos.componentInput'
     */
    @JsonAlias({"componentInput", "jsonData"})
    private String componentInput;

    public ComponentProperties() {
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public Long getStudyId() {
        return this.studyId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    public Timestamp getDate() {
        return this.date;
    }

    public void setHtmlFilePath(String htmlFilePath) {
        this.htmlFilePath = htmlFilePath;
    }

    public String getHtmlFilePath() {
        return this.htmlFilePath;
    }

    public boolean isHtmlFileRename() {
        return htmlFileRename;
    }

    @SuppressWarnings("unused")
    public void setHtmlFileRename(boolean htmlFileRename) {
        this.htmlFileRename = htmlFileRename;
    }

    public boolean isHtmlFileExists() {
        return htmlFileExists;
    }

    public void setHtmlFileExists(boolean htmlFileExists) {
        this.htmlFileExists = htmlFileExists;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getComments() {
        return this.comments;
    }

    public String getComponentInput() {
        return componentInput;
    }

    public void setComponentInput(String componentInput) {
        this.componentInput = componentInput;
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public void setReloadable(boolean reloadable) {
        this.reloadable = reloadable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();

        if (uuid != null && !Jsoup.isValid(uuid, Safelist.none())) {
            errorList.add(new ValidationError(UUID, "HTML is not allowed"));
        }
        if (title == null || title.trim().isEmpty()) {
            errorList.add(new ValidationError(TITLE, "Missing title"));
        }
        if (title != null && title.length() > 255) {
            errorList.add(new ValidationError(TITLE, "Title too long"));
        }
        if (title != null && !Jsoup.isValid(title, Safelist.none())) {
            errorList.add(new ValidationError(TITLE, "HTML is not allowed"));
        }
        if (htmlFilePath != null && !htmlFilePath.trim().isEmpty()) {
            if (htmlFilePath.length() > 255) {
                errorList.add(new ValidationError(HTML_FILE_PATH, "Path too long"));
            }
            // This regular expression defines how a file path should look like
            String pathRegEx = "^[\\w\\d_-][\\w\\d/_-]*\\.[\\w\\d_-]+$";
            if (!(htmlFilePath.matches(pathRegEx))) {
                errorList.add(
                        new ValidationError(HTML_FILE_PATH, "Not a valid path or filename. Remember to "
            + "use '/' as folder separator, and to include the file extension. You can leave it empty for now."));
            }
        }
        if (comments != null && !Jsoup.isValid(comments, Safelist.none())) {
            errorList.add(new ValidationError(COMMENTS, "HTML is not allowed"));
        }
        if (!Strings.isNullOrEmpty(componentInput) && !DefaultJson.isValid(componentInput)) {
            errorList.add(new ValidationError(COMPONENT_INPUT, "Invalid JSON format"));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return id + " " + title;
    }

}
