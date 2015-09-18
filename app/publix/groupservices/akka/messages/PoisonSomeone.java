package publix.groupservices.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange (2015)
 */
public class PoisonSomeone {

	public long studyResultIdOfTheOneToPoison;

	public PoisonSomeone(long studyResultIdOfTheOneToPoison) {
		this.studyResultIdOfTheOneToPoison = studyResultIdOfTheOneToPoison;
	}

}
