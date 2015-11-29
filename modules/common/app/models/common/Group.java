package models.common;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

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
	private Long id;

	private String title;

	/**
	 * Is this group allowed to send messages between each other.
	 */
	private boolean messaging = false;

	/**
	 * Minimum number of workers in the group that are active at the same time.
	 */
	private int minActiveMemberSize = 2;

	/**
	 * Maximum number of workers in the group that are active at the same time.
	 * If there is no limit in active members the value is null.
	 */
	private Integer maxActiveMemberSize = null;

	/**
	 * Maximum number of workers in total. If there is no limit in active
	 * members the value is null.
	 */
	private Integer maxTotalMemberSize = null;

	public Group() {
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return this.title;
	}

	public boolean isMessaging() {
		return messaging;
	}

	public void setMessaging(boolean messaging) {
		this.messaging = messaging;
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
