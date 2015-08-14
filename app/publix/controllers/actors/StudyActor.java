package publix.controllers.actors;

import java.util.HashMap;
import java.util.Map;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class StudyActor extends UntypedActor {

	public static final String NAME = "study-actor";
	
	private static Map<Long, ActorRef> studyGroupMap = new HashMap<>();

	public static Props props() {
		return Props.create(StudyActor.class);
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof String) {
			
		}
	}

	@Override
	public void postStop() throws Exception {
	}
}
