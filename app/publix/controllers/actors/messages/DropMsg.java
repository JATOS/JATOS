package publix.controllers.actors.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class DropMsg {
	
	public long studyResultId;
	
	public DropMsg(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
