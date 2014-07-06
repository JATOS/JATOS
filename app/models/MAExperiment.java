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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
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

	@ManyToMany(fetch = FetchType.LAZY)
	public Set<MAUser> memberList = new HashSet<MAUser>();

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "componentList_ORDER")
	@JoinColumn(name="experiment_id")
	public List<MAComponent> componentList = new ArrayList<MAComponent>();

	public MAExperiment() {
	}
	
	public void update(String title) {
		this.title = title;
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
	
	public void addComponent(MAComponent component) {
		componentList.add(component);
	}
	
	public void removeComponent(MAComponent component) {
		componentList.remove(component);
	}

	public boolean hasComponent(MAComponent component) {
		return componentList.contains(component);
	}

	public void componentOrderMinusOne(MAComponent component) {
		int index = componentList.indexOf(component);
		if (index > 0) {
			MAComponent prevComponent = componentList.get(index - 1);
			componentOrderSwap(component, prevComponent);
		}
	}

	public void componentOrderPlusOne(MAComponent component) {
		int index = componentList.indexOf(component);
		if (index < (componentList.size() - 1)) {
			MAComponent nextComponent = componentList.get(index + 1);
			componentOrderSwap(component, nextComponent);
		}
	}

	public void componentOrderSwap(MAComponent component1,
			MAComponent component2) {
		int index1 = componentList.indexOf(component1);
		int index2 = componentList.indexOf(component2);
		MAComponent.changeComponentOrder(component1, index2);
		MAComponent.changeComponentOrder(component2, index1);
	}

	public void addMember(MAUser user) {
		memberList.add(user);
	}

	public void removeMember(MAUser user) {
		memberList.remove(user);
	}

	public boolean hasMember(MAUser user) {
		return memberList.contains(user);
	}

	public MAComponent getFirstComponent() {
		if (componentList.size() > 0) {
			return componentList.get(0);
		}
		return null;
	}

	public MAComponent getNextComponent(MAComponent component) {
		int index = componentList.indexOf(component);
		if (index < componentList.size() - 1) {
			componentList.get(index + 1);
		}
		return null;
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
	
	public void refresh() {
		JPA.em().refresh(this);
	}

}
