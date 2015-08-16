package publix.controllers.actors.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class WhichIsMsg {

	public final long studyResultId;

	public WhichIsMsg(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
