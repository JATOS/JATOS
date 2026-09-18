package cluster

import com.google.inject.Provider
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject

/**
 * Selects the local or distributed session-message transport from the JATOS configuration.
 */
class SessionMessageBusProvider @Inject()(config: Config,
                                          actorSystem: ActorSystem,
                                          nodeIdentity: NodeIdentity) extends Provider[SessionMessageBus] {

  override def get(): SessionMessageBus = {
    if (config.getBoolean("jatos.multiNode")) {
      new PekkoSessionMessageBus(actorSystem, nodeIdentity)
    } else {
      new LocalSessionMessageBus
    }
  }
}
