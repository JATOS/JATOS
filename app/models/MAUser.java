package models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

@Entity
public class MAUser {

	@Id
	public String email;

	public String name;

	public String password;
	
	@ManyToMany(mappedBy="memberList")
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
	
	public String validate() {
		if (this.name == null || this.name.isEmpty()) {
			return "Missing Name";
		}
		if (this.email == null || this.email.isEmpty()) {
			return "Missing Email";
		}
		if (this.password == null || this.password.isEmpty()) {
			return "Missing Password";
		}
		return null;
	}
	
	public static MAUser findByEmail(String email) {
		return JPA.em().find(MAUser.class, email);
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
