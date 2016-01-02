package models.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import general.common.MessagesStrings;
import play.data.validation.ValidationError;

/**
 * Model of batch properties for UI (not persisted in DB)
 * 
 * @author Kristian Lange
 */
public class BatchProperties {

	public static final String MIN_ACTIVE_MEMBER_SIZE = "minActiveMemberSize";
	public static final String MAX_ACTIVE_MEMBER_SIZE = "maxActiveMemberSize";
	public static final String MAX_ACTIVE_MEMBER_LIMITED = "maxActiveMemberLimited";
	public static final String MAX_TOTAL_MEMBER_SIZE = "maxTotalMemberSize";
	public static final String MAX_TOTAL_MEMBER_LIMITED = "maxTotalMemberLimited";
	public static final String ALLOWED_WORKER_TYPES = "allowedWorkerTypes";

	private Long id;

	/**
	 * Minimum number of workers in the batch that are active at the same time.
	 */
	private Integer minActiveMemberSize = 2;

	private boolean maxActiveMemberLimited = true;

	/**
	 * Maximum number of workers in the batch that are active at the same time.
	 * If there is no limit in active members the value is null.
	 */
	private Integer maxActiveMemberSize = null;

	private boolean maxTotalMemberLimited = true;

	/**
	 * Maximum number of workers in total. If there is no limit in active
	 * members the value is null.
	 */
	private Integer maxTotalMemberSize = null;

	/**
	 * List of worker types that are allowed to run this study. If the worker
	 * type is not in this list, it has no permission to run this study.
	 */
	private Set<String> allowedWorkerTypes = new HashSet<>();

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public Integer getMinActiveMemberSize() {
		return minActiveMemberSize;
	}

	public void setMinActiveMemberSize(Integer minActiveMemberSize) {
		this.minActiveMemberSize = minActiveMemberSize;
	}

	public boolean isMaxActiveMemberLimited() {
		return maxActiveMemberLimited;
	}

	public void setMaxActiveMemberLimited(boolean maxActiveMemberLimited) {
		this.maxActiveMemberLimited = maxActiveMemberLimited;
	}

	public Integer getMaxActiveMemberSize() {
		return maxActiveMemberSize;
	}

	public void setMaxActiveMemberSize(Integer maxActiveMemberSize) {
		this.maxActiveMemberSize = maxActiveMemberSize;
	}

	public boolean isMaxTotalMemberLimited() {
		return maxTotalMemberLimited;
	}

	public void setMaxTotalMemberLimited(boolean maxTotalMemberLimited) {
		this.maxTotalMemberLimited = maxTotalMemberLimited;
	}

	public Integer getMaxTotalMemberSize() {
		return maxTotalMemberSize;
	}

	public void setMaxTotalMemberSize(Integer maxTotalMemberSize) {
		this.maxTotalMemberSize = maxTotalMemberSize;
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
		return String.valueOf(id);
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (minActiveMemberSize == null || minActiveMemberSize < 1) {
			errorList.add(new ValidationError(MIN_ACTIVE_MEMBER_SIZE,
					MessagesStrings.BATCH_MIN_ACTIVE_MEMBER_SIZE));
		}
		if (maxActiveMemberLimited && maxActiveMemberSize == null) {
			errorList.add(new ValidationError(MAX_ACTIVE_MEMBER_SIZE,
					MessagesStrings.BATCH_MAX_ACTIVE_MEMBER_SIZE_SET));
		}
		if (maxActiveMemberLimited && maxActiveMemberSize != null
				&& minActiveMemberSize != null
				&& maxActiveMemberSize < minActiveMemberSize) {
			errorList.add(new ValidationError(MAX_ACTIVE_MEMBER_SIZE,
					MessagesStrings.BATCH_MAX_ACTIVE_MEMBER_SIZE));
		}
		if (maxTotalMemberLimited && maxTotalMemberSize == null) {
			errorList.add(new ValidationError(MAX_TOTAL_MEMBER_SIZE,
					MessagesStrings.BATCH_MAX_TOTAL_MEMBER_SIZE_SET));
		}
		if (maxTotalMemberLimited && maxTotalMemberSize != null
				&& maxActiveMemberSize != null
				&& maxTotalMemberSize < maxActiveMemberSize) {
			errorList.add(new ValidationError(MAX_TOTAL_MEMBER_SIZE,
					MessagesStrings.BATCH_MAX_TOTAL_MEMBER_SIZE));
		}
		return errorList.isEmpty() ? null : errorList;
	}

}
