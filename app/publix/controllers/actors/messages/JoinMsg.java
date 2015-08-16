package publix.controllers.actors.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class JoinMsg {

	public final long studyResultId;

	public JoinMsg(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
