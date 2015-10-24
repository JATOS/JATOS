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
 * Model and DB entity of a group. Default values, where necessary, are at the
 * fields or the constructor.
 * 
 * @author Kristian Lange
 */
@Entity
@Table(name = "\"Group\"")
public class Group {

	public static final String ID = "id";
	public static final String MIN_MEMBER_SIZE = "minMemberSize";
	public static final String MAX_MEMBER_SIZE = "maxMemberSize";
	public static final String MAX_WORKER_SIZE = "maxWorkerSize";

	@Id
	@GeneratedValue
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

	/**
	 * Minimum number of workers at the same time. Is at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int minMemberSize = 2;

	/**
	 * Maximum number of workers at the same time. Is at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int maxMemberSize = 2;

	/**
	 * Maximum number of workers altogether. Is at least 2.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int maxWorkerSize = 2;

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
