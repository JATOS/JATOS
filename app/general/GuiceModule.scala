package general

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import general.common.Common

class GuiceModule(config: Config) extends AbstractModule {

  // A check in OnStartStop's constructor would run after its database dependencies are created.
  H2DatabaseGuard.validate(config.getString("db.default.url"))

  override def configure(): Unit = {
    // JATOS startup initialisation (eager -> called during JATOS start)
    bind(classOf[Common]).asEagerSingleton()
    bind(classOf[OnStartStop]).asEagerSingleton()
  }
}
