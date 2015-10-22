package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import play.data.validation.ValidationError;
import utils.MessagesStrings;

/**
 * Model and DB entity of a group. Default values, where necessary, are at the
 * fields or the constructor.
 * 
 * @author Kristian Lange
 */
@Entity
public class GroupModel {

	public static final String ID = "id";
	public static final String MIN_MEMBER_SIZE = "minGroupSize";
	public static final String MAX_MEMBER_SIZE = "maxGroupSize";
	public static final String MAX_WORKER_SIZE = "maxWorkerSize";

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Minimum number of workers at the same time. Is at least 2.
	 */
	private int minMemberSize = 2;

	/**
	 * Maximum number of workers at the same time. Is at least 2.
	 */
	private int maxMemberSize = 2;

	/**
	 * Maximum number of workers altogether. Is at least 2. If it's null the
	 * number is unlimited.
	 */
	private Integer maxWorkerSize = null;
	
	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private StudyModel study;

	public GroupModel() {
	}
	
	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public int getMinMemberSize() {
		return minMemberSize;
	}

	public void setMinMemberSize(int minMemberSize) {
		this.minMemberSize = minMemberSize;
	}

	public int getMaxMemberSize() {
		return maxMemberSize;
	}

	public void setMaxMemberSize(int maxMemberSize) {
		this.maxMemberSize = maxMemberSize;
	}
	
	public int getMaxWorkerSize() {
		return maxWorkerSize;
	}

	public void setMaxWorkerSize(int maxWorkerSize) {
		this.maxWorkerSize = maxWorkerSize;
	}
	
	public StudyModel getStudy() {
		return this.study;
	}
	
	public void setStudy(StudyModel study) {
		this.study = study;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (minMemberSize < 2) {
			errorList.add(new ValidationError(MIN_MEMBER_SIZE,
					MessagesStrings.GROUP_MEMBER_SIZE));
		}
		if (maxMemberSize < 2) {
			errorList.add(new ValidationError(MAX_MEMBER_SIZE,
					MessagesStrings.GROUP_MEMBER_SIZE));
		}
		if (maxMemberSize < minMemberSize) {
			errorList.add(new ValidationError(MAX_MEMBER_SIZE,
					MessagesStrings.GROUP_MAX_MEMBER_SIZE));
		}
		if (maxWorkerSize != null && maxWorkerSize < 2) {
			errorList.add(new ValidationError(MAX_WORKER_SIZE,
					MessagesStrings.GROUP_WORKER_SIZE));
		}
		if (maxWorkerSize >= maxMemberSize) {
			errorList.add(new ValidationError(MAX_MEMBER_SIZE,
					MessagesStrings.GROUP_MAX_WORKER_SIZE));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	@Override
	public String toString() {
		return String.valueOf(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof GroupModel)) {
			return false;
		}
		GroupModel other = (GroupModel) obj;
		if (id == null) {
			if (other.getId() != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

}
