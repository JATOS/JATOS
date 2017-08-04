package general

import com.google.inject.AbstractModule
import general.common.Common
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import utils.common.JsonObjectMapper

class GuiceConfig extends AbstractModule with AkkaGuiceSupport {

  private val logger: Logger = Logger(this.getClass)

  def configure() = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[Initializer]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
    bind(classOf[JsonObjectMapper]).asEagerSingleton()
  }
}
