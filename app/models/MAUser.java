package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

@Entity
public class MAUser {

	@Id
	public String email;

	public String name;

	public String password;

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
	
	public static MAUser findById(String email) {
		return JPA.em().find(MAUser.class, email);
	}

}
