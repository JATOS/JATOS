package models.gui;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import general.common.MessagesStrings;
import play.data.validation.ValidationError;
import utils.common.IOUtils;
import utils.common.JsonUtils;

/**
 * Model of study properties for UI (not persisted in DB). Only used together
 * with an HTML form that creates a new Study or updates one. The corresponding
 * database entity is {@link models.common.Study}.
 *
 * @author Kristian Lange
 */
public class StudyProperties {

    public static final String STUDY_ID = "studyId";
    public static final String UUID = "uuid";
    public static final String TITLE = "title";
    public static final String JSON_DATA = "jsonData";
    public static final String DESCRIPTION = "description";
    public static final String DIRNAME = "dirName";
    public static final String COMMENTS = "comments";
    public static final String GROUP_STUDY = "groupStudy";
    public static final String LOCKED = "locked";

    public static final String[] INVALID_DIRNAMES = {"jatos", "publix",
            "public", "assets", "study_assets_root", "study_assets"};

    private Long studyId;

    /**
     * Universally (world-wide) unique ID. Used for import/export between
     * different JATOS instances. On one JATOS instance it is only allowed to
     * have one study with the same UUID.
     */
    private String uuid;

    private String title;

    private String description;

    /**
     * Timestamp of the creation or the last update of this study
     */
    private Timestamp date;

    /**
     * If a study is locked, it can't be changed.
     */
    private boolean locked = false;

    /**
     * Is this study a group study, e.g. worker scripts can send messages
     * between each other.
     */
    private boolean groupStudy = false;

    /**
     * Study assets directory name
     */
    private String dirName;

    /**
     * User comments, reminders, something to share with others. They have no
     * further meaning.
     */
    private String comments;

    /**
     * Data in JSON format that are responded after public APIs 'getData' call.
     */
    private String jsonData;

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public Long getStudyId() {
        return this.studyId;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDirName(String dirName) {
        this.dirName = dirName;
    }

    public String getDirName() {
        return this.dirName;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getComments() {
        return this.comments;
    }

    public void setDate(Timestamp timestamp) {
        this.date = timestamp;
    }

    public Timestamp getDate() {
        return this.date;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return this.locked;
    }

    public boolean isGroupStudy() {
        return groupStudy;
    }

    public void setGroupStudy(boolean groupStudy) {
        this.groupStudy = groupStudy;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }

    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) {
            errorList.add(
                    new ValidationError(TITLE, MessagesStrings.MISSING_TITLE));
        }
        if (title != null && title.length() > 255) {
            errorList.add(
                    new ValidationError(TITLE, MessagesStrings.TITLE_TOO_LONG));
        }
        if (title != null && !Jsoup.isValid(title, Whitelist.none())) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (description != null
                && !Jsoup.isValid(description, Whitelist.none())) {
            errorList.add(new ValidationError(DESCRIPTION, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (dirName == null || dirName.trim().isEmpty()) {
            errorList.add(new ValidationError(DIRNAME, MessagesStrings.MISSING_DIRNAME));
        }
        if (dirName != null && dirName.length() > 255) {
            errorList.add(new ValidationError(DIRNAME, MessagesStrings.DIRNAME_TOO_LONG));
        }
        Pattern pattern = Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_FILENAME);
        Matcher matcher = pattern.matcher(dirName);
        if (dirName != null && matcher.find()) {
            errorList.add(new ValidationError(DIRNAME, MessagesStrings.INVALID_DIR_NAME));
        }
        if (dirName != null && Arrays.asList(INVALID_DIRNAMES).contains(dirName)) {
            errorList.add(new ValidationError(DIRNAME, MessagesStrings.INVALID_DIR_NAME));
        }
        if (comments != null && !Jsoup.isValid(comments, Whitelist.none())) {
            errorList.add(new ValidationError(COMMENTS, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (!Strings.isNullOrEmpty(jsonData) && !JsonUtils.isValid(jsonData)) {
            errorList.add(new ValidationError(JSON_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return String.valueOf(studyId) + " " + title;
    }

}
