package publix.groupservices.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class JoinGroup {

	public final long studyResultId;

	public JoinGroup(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
