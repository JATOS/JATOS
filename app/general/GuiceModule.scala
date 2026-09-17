package general

import com.google.inject.AbstractModule
import general.common.Common

class GuiceModule extends AbstractModule {

  override def configure(): Unit = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
  }
}
