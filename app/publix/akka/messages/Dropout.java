package publix.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class Dropout {
	
	public long studyResultId;
	
	public Dropout(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
