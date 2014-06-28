package models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.TypedQuery;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;

@Entity
public class MAUser {

	@Id
	public String email;

	public String name;

	public String password;

	@ManyToMany(mappedBy = "memberList")
	public Set<MAExperiment> experimentList = new HashSet<MAExperiment>();

	public MAUser(String email, String name, String password) {
		this.email = email;
		this.name = name;
		this.password = password;
	}

	public MAUser() {
	}

	@Override
	public String toString() {
		return name + ", " + email;
	}

	public static MAUser authenticate(String email, String password) {
		String queryStr = "SELECT e FROM MAUser e WHERE "
				+ "e.email=:email and e.password=MD5(:password)";
		TypedQuery<MAUser> query = JPA.em().createQuery(queryStr, MAUser.class);
		List<MAUser> userList = query.setParameter("email", email)
				.setParameter("password", password).getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (this.email == null || this.email.isEmpty()) {
			errorList.add(new ValidationError("email", "Missing Email"));
		}
		if (this.name == null || this.name.isEmpty()) {
			errorList.add(new ValidationError("name", "Missing Name"));
		}
		if (this.password == null || this.password.isEmpty()) {
			errorList.add(new ValidationError("password", "Missing Password"));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	public static MAUser findByEmail(String email) {
		return JPA.em().find(MAUser.class, email);
	}
	
	public static List<MAUser> findAll() {
		TypedQuery<MAUser> query = JPA.em().createQuery(
				"SELECT e FROM MAUser e", MAUser.class);
		return query.getResultList();
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
