package cluster

import com.google.inject.Provider
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject

/**
 * Selects the local or distributed channel-message transport from the JATOS configuration.
 */
class ChannelMessageBusProvider @Inject()(config: Config,
                                          actorSystem: ActorSystem,
                                          nodeIdentity: NodeIdentity) extends Provider[ChannelMessageBus] {

  override def get(): ChannelMessageBus = {
    if (config.getBoolean("jatos.multiNode")) {
      new PekkoChannelMessageBus(actorSystem, nodeIdentity)
    } else {
      new LocalChannelMessageBus
    }
  }
}
