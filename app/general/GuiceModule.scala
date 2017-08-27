package general

import com.google.inject.AbstractModule
import general.common.Common
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport

class GuiceModule extends AbstractModule with AkkaGuiceSupport {

  def configure() = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[Initializer]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
  }
}
