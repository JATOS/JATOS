package publix.controllers.actors;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import play.libs.Akka;
import models.GroupResult;
import akka.actor.ActorRef;

@Singleton
public class GroupActorAllocator {

	private Map<Long, Map<Long, ActorRef>> studyGroupMap = new HashMap<Long, Map<Long, ActorRef>>();

	public ActorRef allocate(GroupResult groupResult) {
		Long studyId = groupResult.getStudy().getId();
		Map<Long, ActorRef> studyMap = studyGroupMap.get(studyId);
		if (studyMap == null) {
			studyMap = new HashMap<Long, ActorRef>();
			studyGroupMap.put(studyId, studyMap);
		}

		ActorRef groupActorRef = studyMap.get(groupResult.getId());
		if (groupActorRef == null) {
			groupActorRef = Akka.system().actorOf(GroupActor.props());
			studyMap.put(groupResult.getId(), groupActorRef);
		}
		return groupActorRef;
	}
	
	public void removeGroupActor(GroupResult groupResult) {
		Long studyId = groupResult.getStudy().getId();
		Map<Long, ActorRef> studyMap = studyGroupMap.get(studyId);
		if (studyMap != null) {
			studyMap.remove(groupResult.getId());
		}
	}
}
