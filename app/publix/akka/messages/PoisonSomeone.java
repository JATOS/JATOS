package publix.akka.messages;

/**
 * Message an GroupChannelActor can send to join a GroupActor.
 * 
 * @author Kristian Lange
 */
public class PoisonSomeone {
	
	public long idOfTheOneToPoison;
	
	public PoisonSomeone(long idOfTheOneToPoison) {
		this.idOfTheOneToPoison = idOfTheOneToPoison;
	}

}
