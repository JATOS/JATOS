package general.session

import cluster.{DispatcherChannelMessageReceiver, NodeIdentity, ChannelMessageBus, ChannelMessageBusProvider, ChannelMessagePublisher, ChannelMessageReceiver, ChannelMessageReceiverRegistrar, ChannelMessageReceiverRegistration}
import com.google.inject.AbstractModule

/** Dependency injection configuration for session messaging. */
class SessionGuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ChannelMessageBus]).toProvider(classOf[ChannelMessageBusProvider]).asEagerSingleton()
    bind(classOf[ChannelMessagePublisher]).to(classOf[ChannelMessageBus])
    bind(classOf[ChannelMessageReceiverRegistrar]).to(classOf[ChannelMessageBus])
    bind(classOf[ChannelMessageReceiver]).to(classOf[DispatcherChannelMessageReceiver]).asEagerSingleton()
    bind(classOf[ChannelMessageReceiverRegistration]).asEagerSingleton()
    bind(classOf[NodeIdentity]).asEagerSingleton()
  }
}
