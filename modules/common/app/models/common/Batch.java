package models.common;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonView;

import models.common.workers.Worker;
import utils.common.JsonUtils;

/**
 * Model of a DB entity of a batch. The corresponding UI model is
 * {@link models.gui.BatchProperties}.
 * 
 * Defines the constrains regarding workers for a batch of a study, e.g. which
 * workers are allowed, how many etc.
 * 
 * @TODO
 * An active member is a member who joined a group and is still member of this
 * group. minActiveMembers, maxActiveMemberLimited, maxActiveMembers,
 * maxTotalMemberLimited and maxTotalMembers are properties for groups.
 * 
 * @author Kristian Lange (2015)
 */
@Entity
@Table(name = "Batch")
public class Batch {

	@Id
	@GeneratedValue
	@JsonView({ JsonUtils.JsonForPublix.class })
	private Long id;

	/**
	 * Title of the batch
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private String title;

	/**
	 * Only active (if true) batches can be used.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private boolean active;

	/**
	 * Minimum number of workers/members in one group of this batch that are
	 * active at the same time. This property is only used if this batch belongs
	 * to a group study.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private int minActiveMembers = 2;

	/**
	 * Maximum number of workers/members in one group of this batch that are
	 * active at the same time. If there is no limit in active members the value
	 * is null. This property is only used if this batch belongs to a group
	 * study.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private Integer maxActiveMembers = null;

	/**
	 * Maximum number of workers/members in one group of this batch in total. If
	 * there is no limit in active members the value is null. This property is
	 * only used if this batch belongs to a group study.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private Integer maxTotalMembers = null;

	/**
	 * Maximum number of workers in this batch in total independent of its
	 * groups. If there is no limit in active members the value is null.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private Integer maxTotalWorkers = null;

	/**
	 * Set of workers that are allowed to run in this batch.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class })
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(name = "batch_id")
	private Set<Worker> allowedWorkers = new HashSet<>();

	/**
	 * Set of worker types that are allowed to run in this batch. If the worker
	 * type is not in this list, it has no permission to run this study.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	@ElementCollection
	private Set<String> allowedWorkerTypes = new HashSet<>();

	public Batch() {
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getMinActiveMembers() {
		return minActiveMembers;
	}

	public void setMinActiveMembers(int minActiveMembers) {
		this.minActiveMembers = minActiveMembers;
	}

	public Integer getMaxActiveMembers() {
		return maxActiveMembers;
	}

	public void setMaxActiveMembers(Integer maxActiveMembers) {
		this.maxActiveMembers = maxActiveMembers;
	}

	public Integer getMaxTotalMembers() {
		return maxTotalMembers;
	}

	public void setMaxTotalMembers(Integer maxTotalMembers) {
		this.maxTotalMembers = maxTotalMembers;
	}

	public Integer getMaxTotalWorkers() {
		return maxTotalWorkers;
	}

	public void setMaxTotalWorkers(Integer maxTotalWorkers) {
		this.maxTotalWorkers = maxTotalWorkers;
	}

	public void setAllowedWorkerTypes(Set<String> allowedWorkerTypes) {
		this.allowedWorkerTypes = allowedWorkerTypes;
	}

	public Set<String> getAllowedWorkerTypes() {
		return this.allowedWorkerTypes;
	}

	public void addAllowedWorkerType(String workerType) {
		allowedWorkerTypes.add(workerType);
	}

	public void removeAllowedWorkerType(String workerType) {
		allowedWorkerTypes.remove(workerType);
	}

	public boolean hasAllowedWorkerType(String workerType) {
		return allowedWorkerTypes.contains(workerType);
	}

	public void setAllowedWorkers(Set<Worker> allowedWorkers) {
		this.allowedWorkers = allowedWorkers;
	}

	public Set<Worker> getAllowedWorkers() {
		return this.allowedWorkers;
	}

	public void addAllowedWorker(Worker worker) {
		allowedWorkers.add(worker);
	}

	public void removeAllowedWorker(Worker worker) {
		allowedWorkers.remove(worker);
	}

	public boolean hasAllowedWorker(Worker worker) {
		return allowedWorkers.contains(worker);
	}

	@Override
	public String toString() {
		return String.valueOf(id) + " " + title;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		if (!(obj instanceof Batch)) {
			return false;
		}
		Batch other = (Batch) obj;
		if (id == null) {
			if (other.getId() != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

}
