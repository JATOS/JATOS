package models.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import general.common.MessagesStrings;
import play.data.validation.ValidationError;

/**
 * Model of batch properties for UI (not persisted in DB). Only used together
 * with an HTML form that creates a new Batch or updates one. Default values,
 * where necessary, are at the fields or in the constructor. The corresponding
 * database entity is {@link models.common.Batch}.
 * 
 * An active member is a member who joined a group and is still member of this
 * group. minActiveMembers, maxActiveMemberLimited, maxActiveMembers,
 * maxTotalMemberLimited and maxTotalMembers are properties for groups.
 * 
 * @author Kristian Lange (2015)
 */
public class BatchProperties {

	public static final String TITLE = "title";
	public static final String DEFAULT_TITLE = "Default";
	public static final String ACTIVE = "active";
	public static final String MIN_ACTIVE_MEMBERS = "minActiveMembers";
	public static final String MAX_ACTIVE_MEMBERS = "maxActiveMembers";
	public static final String MAX_ACTIVE_MEMBER_LIMITED = "maxActiveMemberLimited";
	public static final String MAX_TOTAL_MEMBERS = "maxTotalMembers";
	public static final String MAX_TOTAL_MEMBER_LIMITED = "maxTotalMemberLimited";
	public static final String MAX_TOTAL_WORKERS = "maxTotalWorkers";
	public static final String MAX_TOTAL_WORKER_LIMITED = "maxTotalWorkerLimited";
	public static final String ALLOWED_WORKER_TYPES = "allowedWorkerTypes";
	public static final String WORKERS = "workers";

	private Long id;

	/**
	 * Title of the batch
	 */
	private String title;

	/**
	 * True if batch can be used.
	 */
	private boolean active = true;

	/**
	 * Minimum number of workers/members in one group of this batch that are
	 * active at the same time. This property is only used if this batch belongs
	 * to a group study.
	 */
	private Integer minActiveMembers = 2;

	/**
	 * Set to true if the maxActiveMembers are limited (= groups have an limited
	 * number of active members). False otherwise.
	 */
	private boolean maxActiveMemberLimited = false;

	/**
	 * Maximum number of workers/members in one group of this batch that are
	 * active at the same time.
	 */
	private Integer maxActiveMembers = null;

	/**
	 * Set to true if the maxTotalMembers are limited (= groups have a limited
	 * number of members). False otherwise.
	 */
	private boolean maxTotalMemberLimited = false;

	/**
	 * Maximum number of workers/members active or inactive in one group of this
	 * batch in total.
	 */
	private Integer maxTotalMembers = null;

	/**
	 * Set to true if the maxTotalWorkers are limited (= the whole batch has a
	 * limited number of workers). False otherwise.
	 */
	private boolean maxTotalWorkerLimited = false;

	/**
	 * Maximum number of workers in this batch in total independent of its
	 * groups.
	 */
	private Integer maxTotalWorkers = null;

	/**
	 * Set of worker types that are allowed to run in this batch. If the worker
	 * type is not in this list, it has no permission to run this study.
	 */
	private Set<String> allowedWorkerTypes = new HashSet<>();

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
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

	public Integer getMinActiveMembers() {
		return minActiveMembers;
	}

	public void setMinActiveMembers(Integer minActiveMembers) {
		this.minActiveMembers = minActiveMembers;
	}

	public boolean isMaxActiveMemberLimited() {
		return maxActiveMemberLimited;
	}

	public void setMaxActiveMemberLimited(boolean maxActiveMemberLimited) {
		this.maxActiveMemberLimited = maxActiveMemberLimited;
	}

	public Integer getMaxActiveMembers() {
		return maxActiveMembers;
	}

	public void setMaxActiveMembers(Integer maxActiveMembers) {
		this.maxActiveMembers = maxActiveMembers;
	}

	public boolean isMaxTotalMemberLimited() {
		return maxTotalMemberLimited;
	}

	public void setMaxTotalMemberLimited(boolean maxTotalMemberLimited) {
		this.maxTotalMemberLimited = maxTotalMemberLimited;
	}

	public Integer getMaxTotalMembers() {
		return maxTotalMembers;
	}

	public void setMaxTotalMembers(Integer maxTotalMembers) {
		this.maxTotalMembers = maxTotalMembers;
	}

	public boolean isMaxTotalWorkerLimited() {
		return maxTotalWorkerLimited;
	}

	public void setMaxTotalWorkerLimited(boolean maxTotalWorkerLimited) {
		this.maxTotalWorkerLimited = maxTotalWorkerLimited;
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

	public void addAllowedWorkerType(String workerType) {
		allowedWorkerTypes.add(workerType);
	}

	public void removeAllowedWorkerType(String workerType) {
		allowedWorkerTypes.remove(workerType);
	}

	public boolean hasAllowedWorkerType(String workerType) {
		return allowedWorkerTypes.contains(workerType);
	}

	@Override
	public String toString() {
		return String.valueOf(id) + " " + title;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (title == null || title.trim().isEmpty()) {
			errorList.add(
					new ValidationError(TITLE, MessagesStrings.MISSING_TITLE));
		}
		if (title != null && !Jsoup.isValid(title, Whitelist.none())) {
			errorList.add(new ValidationError(TITLE,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		if (minActiveMembers == null || minActiveMembers < 1) {
			errorList.add(new ValidationError(MIN_ACTIVE_MEMBERS,
					MessagesStrings.BATCH_MIN_ACTIVE_MEMBERS));
		}
		if (maxActiveMemberLimited && maxActiveMembers == null) {
			errorList.add(new ValidationError(MAX_ACTIVE_MEMBERS,
					MessagesStrings.BATCH_MAX_ACTIVE_MEMBERS_SET));
		}
		if (maxActiveMemberLimited && maxActiveMembers != null
				&& minActiveMembers != null
				&& maxActiveMembers < minActiveMembers) {
			errorList.add(new ValidationError(MAX_ACTIVE_MEMBERS,
					MessagesStrings.BATCH_MAX_ACTIVE_MEMBERS));
		}
		if (maxTotalMemberLimited && maxTotalMembers == null) {
			errorList.add(new ValidationError(MAX_TOTAL_MEMBERS,
					MessagesStrings.BATCH_MAX_TOTAL_MEMBERS_SET));
		}
		if (maxTotalMemberLimited && maxTotalMembers != null
				&& maxActiveMembers != null
				&& maxTotalMembers < maxActiveMembers) {
			errorList.add(new ValidationError(MAX_TOTAL_MEMBERS,
					MessagesStrings.BATCH_MAX_TOTAL_MEMBERS));
		}
		if (maxTotalWorkers != null && maxTotalWorkers < 1) {
			errorList.add(new ValidationError(MAX_TOTAL_WORKERS,
					MessagesStrings.BATCH_MAX_TOTAL_WORKERS));
		}
		if (maxTotalWorkerLimited && maxTotalWorkers == null) {
			errorList.add(new ValidationError(MAX_TOTAL_WORKERS,
					MessagesStrings.BATCH_MAX_TOTAL_WORKER_SET));
		}

		return errorList.isEmpty() ? null : errorList;
	}

}
