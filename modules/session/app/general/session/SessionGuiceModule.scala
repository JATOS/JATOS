package general.session

import cluster.{IgnoringSessionMessageReceiver, NodeIdentity, RandomNodeIdentity, SessionMessageBus, SessionMessageBusProvider, SessionMessageReceiver}
import com.google.inject.AbstractModule

/** Dependency injection configuration for session messaging. */
class SessionGuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[SessionMessageBus]).toProvider(classOf[SessionMessageBusProvider]).asEagerSingleton()
    bind(classOf[SessionMessageReceiver]).to(classOf[IgnoringSessionMessageReceiver]).asEagerSingleton()
    bind(classOf[NodeIdentity]).to(classOf[RandomNodeIdentity]).asEagerSingleton()
  }
}
