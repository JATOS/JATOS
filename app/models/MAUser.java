package models;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.TypedQuery;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;

@Entity
public class MAUser {

	@Id
	private String email;

	private String name;

	// Password is stored as a hash
	private String passwordHash;

	@ManyToMany(mappedBy = "memberList", fetch=FetchType.LAZY)
	private Set<MAExperiment> experimentList = new HashSet<MAExperiment>();

	public MAUser(String email, String name, String passwordHash) {
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
	}
	
	public MAUser() {
	}

	public void update(String name) {
		this.name = name;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
	
	public String getEmail() {
		return this.email;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}
	
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
	
	public String getPasswordHash() {
		return this.passwordHash;
	}
	
	public void setExperimentList(Set<MAExperiment> experimentList) {
		this.experimentList = experimentList;
	}

	public Set<MAExperiment> getExperimentList() {
		return this.experimentList;
	}
	
	@Override
	public String toString() {
		return name + ", " + email;
	}

	public static MAUser authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM MAUser e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		TypedQuery<MAUser> query = JPA.em().createQuery(queryStr, MAUser.class);
		List<MAUser> userList = query.setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (this.email == null || this.email.isEmpty()) {
			errorList.add(new ValidationError("email", "Missing email"));
		}
		if (this.name == null || this.name.isEmpty()) {
			errorList.add(new ValidationError("name", "Missing name"));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	public static String getHashMDFive(String str)
			throws Exception {
		byte[] strBytes = str.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] hashByte = md.digest(strBytes);

		// Convert the byte to hex format
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < hashByte.length; i++) {
			sb.append(Integer.toString((hashByte[i] & 0xff) + 0x100, 16)
					.substring(1));
		}
		return sb.toString();
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
