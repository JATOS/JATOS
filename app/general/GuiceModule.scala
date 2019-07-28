package general

import com.google.inject.AbstractModule
import general.common.Common
import play.api.libs.concurrent.AkkaGuiceSupport

class GuiceModule extends AbstractModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
  }
}
