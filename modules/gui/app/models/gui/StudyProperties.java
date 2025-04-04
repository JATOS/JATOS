package models.gui;

import com.google.common.base.Strings;
import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Model of study properties for UI (not persisted in DB). Only used together
 * with an HTML form that creates a new Study or updates one. The corresponding
 * database entity is {@link models.common.Study}.
 */
@Constraints.Validate
public class StudyProperties implements Constraints.Validatable<List<ValidationError>> {

    public static final String STUDY_ID = "studyId";
    public static final String UUID = "uuid";
    public static final String TITLE = "title";
    public static final String JSON_DATA = "jsonData";
    public static final String DESCRIPTION = "description";
    public static final String DIR_NAME = "dirName";
    public static final String DIR_RENAME = "dirRename";
    public static final String COMMENTS = "comments";
    public static final String GROUP_STUDY = "groupStudy";
    public static final String LOCKED = "locked";
    public static final String LINEAR_STUDY_FLOW = "linearStudy";
    public static final String END_REDIRECT_URL = "endRedirectUrl";
    public static final String STUDY_ENTRY_MSG = "studyEntryMsg";
    public static final String ALLOW_PREVIEW = "allowPreview";

    public static final String[] INVALID_DIR_NAMES = {"jatos", "publix",
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
     * A study with a linear study flow allows the component position to only increase (no going back to earlier
     * components).
     */
    private boolean linearStudy = false;

    /**
     * If true, allows a preview of the first component of the study
     */
    private boolean allowPreview = false;

    /**
     * Study assets directory name
     */
    private String dirName;

    /**
     * Rename study assets directory (the actual directory on the disk - not just the value in the DB)
     */
    private boolean dirRename;

    /**
     * User comments, reminders, something to share with others. They have no
     * further meaning.
     */
    private String comments;

    /**
     * Data in JSON format that are responded after public APIs 'getData' call.
     */
    private String jsonData;

    /**
     * URL to which should be redirected if the study run finishes. If kept null it won't be redirected and the default
     * endPage will be shown.
     */
    private String endRedirectUrl;

    /**
     * Will be shown to the worker on the Study Entry page
     */
    private String studyEntryMsg;

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

    public boolean isDirRename() {
        return dirRename;
    }

    public void setDirRename(boolean dirRename) {
        this.dirRename = dirRename;
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

    public boolean isLinearStudy() {
        return linearStudy;
    }

    public void setLinearStudy(boolean linearStudy) {
        this.linearStudy = linearStudy;
    }

    public boolean isAllowPreview() {
        return allowPreview;
    }

    public void setAllowPreview(boolean allowPreview) {
        this.allowPreview = allowPreview;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }

    public String getEndRedirectUrl() {
        return endRedirectUrl;
    }

    public void setEndRedirectUrl(String endRedirectUrl) {
        this.endRedirectUrl = endRedirectUrl;
    }

    public String getStudyEntryMsg() {
        return studyEntryMsg;
    }

    public void setStudyEntryMsg(String studyEntryMsg) {
        this.studyEntryMsg = studyEntryMsg;
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
        if (dirName != null) { // Only during study creation, dirName can be null
            if (dirName.trim().isEmpty()) {
                errorList.add(new ValidationError(DIR_NAME, MessagesStrings.MISSING_DIR_NAME));
            }
            if (dirName.length() > 255) {
                errorList.add(new ValidationError(DIR_NAME, MessagesStrings.DIR_NAME_TOO_LONG));
            }
            if (!IOUtils.checkFilename(dirName)) {
                errorList.add(new ValidationError(DIR_NAME, MessagesStrings.INVALID_DIR_NAME));
            }
            if (Arrays.asList(INVALID_DIR_NAMES).contains(dirName)) {
                errorList.add(new ValidationError(DIR_NAME, MessagesStrings.INVALID_DIR_NAME));
            }
        }
        if (comments != null && !Jsoup.isValid(comments, Safelist.none())) {
            errorList.add(new ValidationError(COMMENTS, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (!Strings.isNullOrEmpty(jsonData) && !JsonUtils.isValid(jsonData)) {
            errorList.add(new ValidationError(JSON_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        if (description != null && !Jsoup.isValid(description, Safelist.none())) {
            errorList.add(new ValidationError(DESCRIPTION, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (endRedirectUrl != null && !Jsoup.isValid(endRedirectUrl, Safelist.none())) {
            errorList.add(new ValidationError(END_REDIRECT_URL, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (studyEntryMsg != null && !Jsoup.isValid(studyEntryMsg, Safelist.none())) {
            errorList.add(new ValidationError(STUDY_ENTRY_MSG, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (studyEntryMsg != null && studyEntryMsg.getBytes().length > 1000) {
            errorList.add(new ValidationError(STUDY_ENTRY_MSG, MessagesStrings.STUDY_ENTRY_MSG_TOO_LONG));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    public List<ValidationError> validateDescription() {
        List<ValidationError> errorList = new ArrayList<>();
        if (description != null && !Jsoup.isValid(description, Safelist.none())) {
            errorList.add(new ValidationError(DESCRIPTION, MessagesStrings.NO_HTML_ALLOWED));
        }
        return errorList;
    }

    @Override
    public String toString() {
        return studyId + " " + title;
    }

}
