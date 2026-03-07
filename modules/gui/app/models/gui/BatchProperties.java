package models.gui;

import com.google.common.base.Strings;
import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;
import services.gui.WorkerService;
import utils.common.JsonUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model of batch properties for UI (not persisted in DB). Only used together with an HTML form that creates a new Batch
 * or updates one. Default values, where necessary, are at the fields or in the constructor. The corresponding database
 * entity is {@link models.common.Batch}.
 *
 * An active member is a member who joined a group and is still a member of this group. maxActiveMembers and
 * maxTotalMembers are properties for groups.
 */
@Constraints.Validate
public class BatchProperties implements Validatable<List<ValidationError>> {

    public static final String ID = "id";
    public static final String UUID = "uuid";
    public static final String TITLE = "title";
    public static final String DEFAULT_TITLE = "Default";
    public static final String ACTIVE = "active";
    public static final String MAX_ACTIVE_MEMBERS = "maxActiveMembers";
    public static final String MAX_TOTAL_MEMBERS = "maxTotalMembers";
    public static final String MAX_TOTAL_WORKERS = "maxTotalWorkers";
    public static final String ALLOWED_WORKER_TYPES = "allowedWorkerTypes";
    public static final String COMMENTS = "comments";
    public static final String JSON_DATA = "jsonData";

    private Long id;

    /**
     * Universally (world-wide) unique ID. Used for import/export between different JATOS instances. On one JATOS
     * instance it is only allowed to have one batch with the same UUID.
     */
    private String uuid;

    /**
     * Title of the batch
     */
    private String title;

    /**
     * True if batch can be used.
     */
    private boolean active = true;

    /**
     * Maximum number of workers/members in one group of these batches that are active at the same time.
     */
    private Integer maxActiveMembers = null;

    /**
     * Maximum number of workers/members active or inactive in one group of these batches in total.
     */
    private Integer maxTotalMembers = null;

    /**
     * Maximum number of workers in this batch in total independent of its groups.
     */
    private Integer maxTotalWorkers = null;

    /**
     * Set of worker types that are allowed to run in this batch. If the worker type is not in this list, it has no
     * permission to run this study.
     */
    private Set<String> allowedWorkerTypes = new HashSet<>();

    /**
     * User comments, reminders, something to share with others. They have no further meaning.
     */
    private String comments;

    /**
     * Data in JSON format: every study run of this Batch gets access to them. They can be changed in the GUI but not
     * via jatos.js. Can be used for initial data and configuration.
     */
    private String jsonData;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getMaxActiveMembers() {
        return maxActiveMembers;
    }

    public void setMaxActiveMembers(Integer maxActiveMembers) {
        this.maxActiveMembers = maxActiveMembers;
    }

    public Integer getMaxTotalMembers() {
        return maxTotalMembers;
    }

    public void setMaxTotalMembers(Integer maxTotalMembers) {
        this.maxTotalMembers = maxTotalMembers;
    }

    public Integer getMaxTotalWorkers() {
        return maxTotalWorkers;
    }

    public void setMaxTotalWorkers(Integer maxTotalWorkers) {
        this.maxTotalWorkers = maxTotalWorkers;
    }

    public void setAllowedWorkerTypes(Set<String> allowedWorkerTypes) {
        this.allowedWorkerTypes = allowedWorkerTypes;
    }

    public Set<String> getAllowedWorkerTypes() {
        return this.allowedWorkerTypes;
    }

    public void addAllowedWorkerType(String type) {
        allowedWorkerTypes.add(type);
    }

    public void removeAllowedWorkerType(String type) {
        allowedWorkerTypes.remove(type);
    }

    public boolean hasAllowedWorkerType(String type) {
        return allowedWorkerTypes.contains(type);
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }

    @Override
    public String toString() {
        return id + " " + title;
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
        if (maxActiveMembers != null && maxActiveMembers < 1) {
            errorList.add(new ValidationError(MAX_ACTIVE_MEMBERS, "Batch's max active member size must be at least 1."));
        }
        if (maxTotalMembers != null && maxTotalMembers < 1) {
            errorList.add(new ValidationError(MAX_TOTAL_MEMBERS, "Batch's max total member size must be at least 1."));
        }
        if (maxTotalWorkers != null && maxTotalWorkers < 1) {
            errorList.add(new ValidationError(MAX_TOTAL_WORKERS, "Batch's max total worker size must be at least 1."));
        }
        if (maxTotalMembers != null && maxActiveMembers != null && maxTotalMembers < maxActiveMembers) {
            errorList.add(new ValidationError(MAX_TOTAL_MEMBERS, "Maximum total members must be greater than or equal to maximum active members."));
        }
        if (comments != null && !Jsoup.isValid(comments, Safelist.none())) {
            errorList.add(new ValidationError(COMMENTS, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (!Strings.isNullOrEmpty(jsonData) && !JsonUtils.isValid(jsonData)) {
            errorList.add(new ValidationError(JSON_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        for (String type : allowedWorkerTypes) {
            if (!WorkerService.isValidWorkerType(type)) {
                errorList.add(new ValidationError(ALLOWED_WORKER_TYPES, "Invalid worker type: " + type));
            }
        }
        return errorList.isEmpty() ? null : errorList;
    }

}
