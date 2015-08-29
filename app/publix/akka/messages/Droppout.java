package publix.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class Droppout {
	
	public long studyResultId;
	
	public Droppout(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
