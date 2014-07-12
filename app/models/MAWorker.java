package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;

import play.db.jpa.JPA;

@Entity
public class MAWorker {

	private static final String FAIL = "fail";

	@Id
	public String workerId;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "MAExperiment_confirmationCode")
	@MapKeyColumn(name = "MAExperiment_id")
	@Column(name = "confirmationCode")
	public Map<Long, String> finishedExperimentMap = new HashMap<Long, String>();

	@OneToMany(mappedBy = "worker", fetch = FetchType.LAZY)
	public List<MAResult> resultList = new ArrayList<MAResult>();

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "MAComponent_MAResult")
	@MapKeyColumn(name = "component_id")
	@Column(name = "result_id")
	public Map<Long, Long> currentComponentMap = new HashMap<Long, Long>();

	public MAWorker() {
	}

	public MAWorker(String id) {
		this.workerId = id;
	}

	public boolean hasCurrentComponent(MAComponent component) {
		return currentComponentMap.containsKey(component.id);
	}

	public MAResult getCurrentResult(MAComponent component) {
		Long resultId = currentComponentMap.get(component.id);
		return MAResult.findById(resultId);
	}

	public void addCurrentComponent(MAComponent component, MAResult result) {
		currentComponentMap.put(component.id, result.id);
	}

	public void removeCurrentComponent(MAComponent component) {
		currentComponentMap.remove(component.id);
	}

	/**
	 * Remove all components of this experiment from worker's
	 * currentComponentMap
	 */
	public void removeCurrentComponentsForExperiment(MAExperiment experiment) {
		Iterator<Long> it = currentComponentMap.keySet().iterator();
		while (it.hasNext()) {
			Long componentId = it.next();
			MAComponent component = MAComponent.findById(componentId);
			if (experiment.hasComponent(component)) {
				it.remove();
			}
		}
	}

	public boolean finishedExperiment(Long experimentId) {
		return finishedExperimentMap.containsKey(experimentId);
	}

	public String finishExperiment(Long experimentId, boolean successful) {
		String confirmationCode;
		if (successful) {
			confirmationCode = UUID.randomUUID().toString();
		} else {
			confirmationCode = FAIL;
		}
		finishedExperimentMap.put(experimentId, confirmationCode);
		return confirmationCode;
	}
	
	public String getConfirmationCode(Long experimentId) {
		return finishedExperimentMap.get(experimentId);
	}

	public void addResult(MAResult result) {
		resultList.add(result);
	}

	public void removeResult(MAResult result) {
		resultList.remove(result);
	}

	@Override
	public String toString() {
		return workerId;
	}

	public static MAWorker findById(String id) {
		return JPA.em().find(MAWorker.class, id);
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
