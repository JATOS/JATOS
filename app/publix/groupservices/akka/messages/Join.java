package publix.groupservices.akka.messages;

/**
 * Message a GroupChannel can send to join a GroupDispatcher.
 * 
 * @author Kristian Lange (2015)
 */
public class Join {

	public final long studyResultId;

	public Join(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
