package general.session

import cluster.{DispatcherSessionMessageReceiver, NodeIdentity, SessionMessageBus, SessionMessageBusProvider, SessionMessagePublisher, SessionMessageReceiver, SessionMessageReceiverRegistrar, SessionMessageReceiverRegistration}
import com.google.inject.AbstractModule

/** Dependency injection configuration for session messaging. */
class SessionGuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[SessionMessageBus]).toProvider(classOf[SessionMessageBusProvider]).asEagerSingleton()
    bind(classOf[SessionMessagePublisher]).to(classOf[SessionMessageBus])
    bind(classOf[SessionMessageReceiverRegistrar]).to(classOf[SessionMessageBus])
    bind(classOf[SessionMessageReceiver]).to(classOf[DispatcherSessionMessageReceiver]).asEagerSingleton()
    bind(classOf[SessionMessageReceiverRegistration]).asEagerSingleton()
    bind(classOf[NodeIdentity]).asEagerSingleton()
  }
}
