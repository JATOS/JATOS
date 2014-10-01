package models;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToOne;
import javax.persistence.TypedQuery;

import com.fasterxml.jackson.annotation.JsonIgnore;

import models.workers.MAWorker;
import play.data.validation.ValidationError;
import play.db.jpa.JPA;
import services.ErrorMessages;

/**
 * Domain model and DAO of a user.
 * 
 * @author Kristian Lange
 */
@Entity
public class UserModel {

	public static final String NAME = "name";
	public static final String EMAIL = "email";
	public static final String PASSWORD = "password";
	
	@Id
	private String email;

	private String name;

	@JsonIgnore
	@OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private MAWorker worker;

	// Password is stored as a hash
	private String passwordHash;

	@ManyToMany(mappedBy = "memberList", fetch = FetchType.LAZY)
	private Set<StudyModel> studyList = new HashSet<StudyModel>();

	public UserModel(String email, String name, String passwordHash) {
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
	}

	public UserModel() {
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

	public void setStudyList(Set<StudyModel> studyList) {
		this.studyList = studyList;
	}

	public Set<StudyModel> getStudyList() {
		return this.studyList;
	}

	public void setWorker(MAWorker worker) {
		this.worker = worker;
	}

	public MAWorker getWorker() {
		return this.worker;
	}

	@Override
	public String toString() {
		return name + ", " + email;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((email == null) ? 0 : email.hashCode());
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
		if (!(obj instanceof UserModel)) {
			return false;
		}
		UserModel other = (UserModel) obj;
		if (email == null) {
			if (other.getEmail() != null) {
				return false;
			}
		} else if (!email.equals(other.getEmail())) {
			return false;
		}
		return true;
	}

	public static UserModel authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM UserModel e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		TypedQuery<UserModel> query = JPA.em().createQuery(queryStr,
				UserModel.class);
		List<UserModel> userList = query.setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (this.email == null || this.email.isEmpty()) {
			errorList.add(new ValidationError("email",
					ErrorMessages.MISSING_EMAIL));
		}
		if (this.name == null || this.name.isEmpty()) {
			errorList.add(new ValidationError("name",
					ErrorMessages.MISSING_NAME));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	public static String getHashMDFive(String str)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
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

	public static UserModel findByEmail(String email) {
		return JPA.em().find(UserModel.class, email);
	}

	public static List<UserModel> findAll() {
		TypedQuery<UserModel> query = JPA.em().createQuery(
				"SELECT e FROM UserModel e", UserModel.class);
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
