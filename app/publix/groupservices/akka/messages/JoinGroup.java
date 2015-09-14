package publix.groupservices.akka.messages;

/**
 * Message a GroupChannel can send to join a GroupDispatcher.
 * 
 * @author Kristian Lange (2015)
 */
public class JoinGroup {

	public final long studyResultId;

	public JoinGroup(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
