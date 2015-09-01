package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import models.StudyResult;
import persistance.StudyResultDao;
import play.libs.Akka;
import publix.akka.messages.Dropout;
import publix.akka.messages.Get;
import publix.akka.messages.GetOrCreate;
import publix.akka.messages.ItsThisOne;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Unregister;
import publix.services.GroupService;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Kristian Lange
 */
@Singleton
public class GroupDispatcherRegistry extends UntypedActor {

	public static final String ACTOR_NAME = "GroupDispatcherRegistry";

	// groupResultId -> GroupDispatcher
	private Map<Long, ActorRef> groupDispatcherMap = new HashMap<Long, ActorRef>();
	private final GroupService groupService;
	private final StudyResultDao studyResultDao;

	@Inject
	public GroupDispatcherRegistry(GroupService groupService,
			StudyResultDao studyResultDao) {
		this.groupService = groupService;
		this.studyResultDao = studyResultDao;
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Get) {
			Get get = (Get) msg;
			ActorRef groupDispatcher = groupDispatcherMap.get(get.id);
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		}
		if (msg instanceof GetOrCreate) {
			GetOrCreate getOrCreate = (GetOrCreate) msg;
			ActorRef groupDispatcher = groupDispatcherMap.get(getOrCreate.id);
			if (groupDispatcher == null) {
				groupDispatcher = Akka.system().actorOf(
						GroupDispatcher.props(self(), getOrCreate.id));
				groupDispatcherMap.put(getOrCreate.id, groupDispatcher);
			}
			ItsThisOne answer = new ItsThisOne(groupDispatcher);
			sender().tell(answer, self());
		} else if (msg instanceof Dropout) {
			Dropout droppout = (Dropout) msg;
			StudyResult studyResult = studyResultDao
					.findById(droppout.studyResultId);
			groupService.dropGroupResult(studyResult);
		} else if (msg instanceof Unregister) {
			Unregister unregister = (Unregister) msg;
			groupDispatcherMap.remove(unregister.id);
		} else if (msg instanceof PoisonSomeone) {
			PoisonSomeone poison = (PoisonSomeone) msg;
			ActorRef actorRef = groupDispatcherMap
					.get(poison.idOfTheOneToPoison);
			if (actorRef != null) {
				actorRef.forward(msg, getContext());
			}
		}
	}
}