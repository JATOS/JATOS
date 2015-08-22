package publix.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class ClosedSystemChannel {

	public long studyResultId;
	
	public ClosedSystemChannel(long studyResultId) {
		this.studyResultId = studyResultId;
	}
	
}
