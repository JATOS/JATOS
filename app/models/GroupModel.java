package models;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

/**
 * Domain model of a group.
 * 
 * @author Kristian Lange
 */
@Entity
public class GroupModel {

	@Id
	@GeneratedValue
	private Long id;

	private int maxWorker;

	@OneToOne(mappedBy = "group", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private StudyModel study;

	public GroupModel() {
	}

	public GroupModel(StudyModel study, int maxWorker) {
		this.study = study;
		this.maxWorker = maxWorker;
	}
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getMaxWorker() {
		return maxWorker;
	}

	public void setMaxWorker(int maxWorker) {
		this.maxWorker = maxWorker;
	}

	public StudyModel getStudy() {
		return study;
	}

	public void setStudy(StudyModel study) {
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
