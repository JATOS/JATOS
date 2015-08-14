package publix.controllers.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class GroupChannelActor extends UntypedActor {

	private final ActorRef out;
	private final ActorRef groupActor;
	private final long studyId;

	public static Props props(ActorRef out, long studyId, ActorRef groupActor) {
		return Props.create(GroupChannelActor.class, out, studyId, groupActor);
	}

	public GroupChannelActor(ActorRef out, long studyId, ActorRef groupActor) {
		this.out = out;
		this.studyId = studyId;
		this.groupActor = groupActor;//getContext().actorSelection(
//				"akka://application/user/" + GroupActor.NAME);
	}

	@Override
	public void preStart() {
		groupActor.tell(new JoinMessage(studyId), self());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof String) {
			groupActor.tell(new GroupMessage(msg), self());
		} else if (msg instanceof GroupMessage) {
			out.tell(msg.toString(), self());
		}
	}

}
