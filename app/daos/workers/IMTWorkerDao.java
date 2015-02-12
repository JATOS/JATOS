package daos.workers;

import models.workers.MTWorker;

/**
 * Interface for DAO of MTWorker model
 * 
 * @author Kristian Lange
 */
public interface IMTWorkerDao {

	/**
	 * Create MTWorker. Distinguishes between normal MechTurk and Sandbox
	 * MechTurk via mTurkSandbox parameter.
	 */
	public abstract MTWorker create(String mtWorkerId,
			boolean mTurkSandbox);

	/**
	 * Retrieves the worker with the given MTurk worker ID in a case insensitive
	 * way.
	 */
	public abstract MTWorker findByMTWorkerId(String mtWorkerId);

}