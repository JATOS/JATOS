package general

import com.google.inject.AbstractModule
import general.common.Common
import play.libs.pekko.PekkoGuiceSupport

class GuiceModule extends AbstractModule with PekkoGuiceSupport {

  override def configure(): Unit = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
  }
}
