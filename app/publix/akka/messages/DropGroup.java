package publix.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class DropGroup {
	
	public long studyResultId;
	
	public DropGroup(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
