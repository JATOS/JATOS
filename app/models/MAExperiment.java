package models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.TypedQuery;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;

@Entity
public class MAExperiment {

	@Id
	@GeneratedValue
	public Long id;

	public String title;

	public Timestamp date;

	@ManyToMany
	public Set<MAUser> memberList = new HashSet<MAUser>();

	@OneToMany(mappedBy = "experiment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	public List<MAComponent> componentList = new ArrayList<MAComponent>();

	public MAExperiment() {
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (this.title == null || this.title.isEmpty()) {
			errorList.add(new ValidationError("title", "Missing title"));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	@Override
	public String toString() {
		return id + ", " + title;
	}

	public static MAExperiment findById(Long id) {
		return JPA.em().find(MAExperiment.class, id);
	}

	public static List<MAExperiment> findAll() {
		TypedQuery<MAExperiment> query = JPA.em().createQuery(
				"SELECT e FROM MAExperiment e", MAExperiment.class);
		return query.getResultList();
	}
	
	public void addMember(MAUser user) {
		memberList.add(user);
	}
	
	public void removeMember(MAUser user) {
		memberList.remove(user);
	}
	
	public boolean isMember(MAUser user) {
		return memberList.contains(user);
	}
		
	public void persist() {
		JPA.em().persist(this);
	}

	public void merge() {
		JPA.em().merge(this);
	}

	public void remove() {
		JPA.em().remove(this);
	}

}
