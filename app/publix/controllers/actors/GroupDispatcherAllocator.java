package publix.controllers.actors;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import models.GroupResult;
import play.libs.Akka;
import publix.controllers.actors.actors.GroupDispatcher;
import akka.actor.ActorRef;

/**
 * Find, creates and stores GroupActors for GroupResults.
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupDispatcherAllocator {

	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();

	/**
	 * Returns the GroupActor reference this GroupResult belongs to. If it
	 * doesn't belong to any it creates a new GroupActor and stores it.
	 */
	public ActorRef allocateGroupDispatcher(GroupResult groupResult) {
		ActorRef groupDispatcher = groupDispatcherMap.get(groupResult.getId());
		if (groupDispatcher == null) {
			groupDispatcher = Akka.system().actorOf(
					GroupDispatcher.props());
			groupDispatcherMap.put(groupResult.getId(), groupDispatcher);
		}
		return groupDispatcher;
	}

	public ActorRef removeGroupDispatcher(GroupResult groupResult) {
		if (groupResult == null) {
			return null;
		}
		return groupDispatcherMap.remove(groupResult.getId());
	}

}
