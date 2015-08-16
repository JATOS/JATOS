package publix.controllers.actors.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class PoisonMsg {
	
	public long studyResultId;
	
	public PoisonMsg(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
