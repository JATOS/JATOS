package models.common;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import utils.common.JsonUtils;

/**
 * Model of a DB entity of a group with all properties of a group (without the
 * results). The results are stored in GroupResults. Default values, where
 * necessary, are at the fields or the constructor.
 * 
 * We can't use 'Group' for the database table name, since it's a keyword in
 * some databases (therfore the 'Groupp').
 * 
 * @author Kristian Lange (2015)
 */
@Entity
@Table(name = "Groupp")
public class Group {

	@Id
	@GeneratedValue
	@JsonView({ JsonUtils.JsonForPublix.class })
	private Long id;

	/**
	 * Minimum number of workers in the group that are active at the same time.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class })
	private int minActiveMemberSize = 2;

	/**
	 * Maximum number of workers in the group that are active at the same time.
	 * If there is no limit in active members the value is null.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class })
	private Integer maxActiveMemberSize = null;

	/**
	 * Maximum number of workers in total. If there is no limit in active
	 * members the value is null.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class })
	private Integer maxTotalMemberSize = null;

	/**
	 * List of worker types that are allowed to run this study. If the worker
	 * type is not in this list, it has no permission to run this study.
	 */
	@ElementCollection
	@JsonIgnore
	private Set<String> allowedWorkerTypes = new HashSet<>();

	public Group() {
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public int getMinActiveMemberSize() {
		return minActiveMemberSize;
	}

	public void setMinActiveMemberSize(int minActiveMemberSize) {
		this.minActiveMemberSize = minActiveMemberSize;
	}

	public Integer getMaxActiveMemberSize() {
		return maxActiveMemberSize;
	}

	public void setMaxActiveMemberSize(Integer maxActiveMemberSize) {
		this.maxActiveMemberSize = maxActiveMemberSize;
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
		if (!(obj instanceof Group)) {
			return false;
		}
		Group other = (Group) obj;
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
