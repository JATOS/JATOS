package publix.groupservices.akka.messages;

/**
 * Message an GroupChannel can send to a GroupDispatcher to indicate it's
 * closure.
 * 
 * @author Kristian Lange (2015)
 */
public class ChannelClosed {

	public long studyResultId;

	public ChannelClosed(long studyResultId) {
		this.studyResultId = studyResultId;
	}

}
