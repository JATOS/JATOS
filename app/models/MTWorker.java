package models;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import play.db.jpa.JPA;

@Entity
public class MTWorker {

	@Id
	public String workerId;

//	@ManyToMany(targetEntity = MTWorker.class, cascade = CascadeType.ALL)
//	public Map<Long, String> confirmationCodeMap = new HashMap<Long, String>();

	public MTWorker() {
	}

	public MTWorker(String workerId) {
		this.workerId = workerId;
	}

//	public void putConfirmationCode(long experimentId, String confirmationCode) {
//		confirmationCodeMap.put(experimentId, confirmationCode);
//	}

	@Override
	public String toString() {
		return workerId;
	}

	public static MTWorker findById(String workerId) {
		return JPA.em().find(MTWorker.class, workerId);
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
