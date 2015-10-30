package models;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import utils.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Model of a DB entity of a group with all properties of a group but not the
 * results. The results are stored in GroupResults. Default values, where
 * necessary, are at the fields or the constructor.
 * 
 * @author Kristian Lange (2015)
 */
@Entity
@Table(name = "Groupp")
public class Group {

	@Id
	@GeneratedValue
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

	/**
	 * Minimum number of workers in the group that are active at the same time.
	 * It's at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int minActiveMemberSize = 2;

	/**
	 * Maximum number of workers in the group that are active at the same time.
	 * It's at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int maxActiveMemberSize = 2;

	/**
	 * Maximum number of workers that are allowed to be member in the group
	 * altogether (active and inactive together). It's at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int maxTotalMemberSize = 2;

	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	private Study study;

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

	public int getMaxActiveMemberSize() {
		return maxActiveMemberSize;
	}

	public void setMaxActiveMemberSize(int maxActiveMemberSize) {
		this.maxActiveMemberSize = maxActiveMemberSize;
	}

	public int getMaxTotalMemberSize() {
		return maxTotalMemberSize;
	}

	public void setMaxTotalMemberSize(int maxTotalMemberSize) {
		this.maxTotalMemberSize = maxTotalMemberSize;
	}

	public Study getStudy() {
		return this.study;
	}

	public void setStudy(Study study) {
		this.study = study;
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
