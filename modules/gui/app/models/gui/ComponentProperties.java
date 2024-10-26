package models.gui;

import com.google.common.base.Strings;
import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import utils.common.JsonUtils;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Model of component properties for UI (not persisted in DB). Only used together with an HTML form that creates a new
 * Component or updates one. The corresponding database entity is {@link models.common.Component}.
 */
@Constraints.Validate
public class ComponentProperties implements Constraints.Validatable<List<ValidationError>> {

    /**
     * Version of this model used for serialisation (e.g. JSON marshaling)
     */
    public static final int SERIAL_VERSION = 1;

    public static final String ID = "id";
    public static final String UUID = "uuid";
    public static final String TITLE = "title";
    public static final String HTML_FILE_PATH = "htmlFilePath";
    public static final String HTML_FILE_RENAME = "htmlFileRename";
    public static final String HTML_FILE_EXISTS = "htmlFileExists";
    public static final String JSON_DATA = "jsonData";
    public static final String RELOADABLE = "reloadable";
    public static final String ACTIVE = "active";
    public static final String COMMENTS = "comments";
    public static final String COMPONENT = "component";

    private Long id;

    /**
     * Universally, (world-wide) unique ID. Used for import/export between different JATOS instances. A study can have
     * only one component with the same UUID, although it is allowed to have other studies that have this component with
     * this UUID.
     */
    private String uuid;

    private Long studyId;

    private String title;

    /**
     * Timestamp of the creation or the last update of this component
     */
    private Timestamp date;

    /**
     * Local path to component's HTML file in the study assets' dir. File separators are persisted as '/'.
     */
    private String htmlFilePath;

    /**
     * Should the actual HTML file on the disk be renamed - or just the value in the DB?
     */
    private boolean htmlFileRename;

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

    private String jsonData;

    public ComponentProperties() {}

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
        if (htmlFilePath != null) {
            return this.htmlFilePath.replace('/', File.separatorChar);
        } else {
            return null;
        }
    }

    public boolean isHtmlFileRename() {
        return htmlFileRename;
    }

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

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
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
            errorList.add(new ValidationError(UUID, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (title == null || title.trim().isEmpty()) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.MISSING_TITLE));
        }
        if (title != null && title.length() > 255) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.TITLE_TOO_LONG));
        }
        if (title != null && !Jsoup.isValid(title, Safelist.none())) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (htmlFilePath != null && !htmlFilePath.trim().isEmpty()) {
            if (htmlFilePath.length() > 255) {
                errorList.add(new ValidationError(HTML_FILE_PATH, MessagesStrings.PATH_TOO_LONG));
            }
            // This regular expression defines how a file path should look like
            String pathRegEx = "^[\\w\\d_-][\\w\\d/_-]*\\.[\\w\\d_-]+$";
            if (!(htmlFilePath.matches(pathRegEx))) {
                errorList.add(
                        new ValidationError(HTML_FILE_PATH, MessagesStrings.NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY));
            }
        }
        if (comments != null && !Jsoup.isValid(comments, Safelist.none())) {
            errorList.add(new ValidationError(COMMENTS, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (!Strings.isNullOrEmpty(jsonData) && !JsonUtils.isValid(jsonData)) {
            errorList.add(new ValidationError(JSON_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return id + " " + title;
    }

}
