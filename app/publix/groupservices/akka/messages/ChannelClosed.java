package publix.groupservices.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class ChannelClosed {
	
	public long studyResultId;
	
	public ChannelClosed(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
