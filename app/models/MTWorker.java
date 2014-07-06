package models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKeyColumn;

import play.db.jpa.JPA;

@Entity
public class MTWorker {

	@Id
	public String workerId;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "MAExperiment_confirmationCode")
	@MapKeyColumn(name = "MAExperiment_id")
	@Column(name = "confirmationCode")
	public Map<Long, String> confirmationCodeMap = new HashMap<Long, String>();

	public MTWorker() {
	}

	public MTWorker(String workerId) {
		this.workerId = workerId;
	}

	public boolean didFinishExperiment(Long experimentId) {
		return confirmationCodeMap.containsKey(experimentId);
	}

	public String finishExperiment(Long experimentId) {
		String confirmationCode = UUID.randomUUID().toString();
		confirmationCodeMap.put(experimentId, confirmationCode);
		return confirmationCode;
	}

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
