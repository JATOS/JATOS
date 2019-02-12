package general

import general.common.Common
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Called during start-up
  *
  * @author Kristian Lange
  */
@Singleton
class OnStartStop @Inject()(lifecycle: ApplicationLifecycle, environment: play.Environment) {

  private val logger = Logger(this.getClass)

  logger.info("JATOS has started")
  if (environment.isProd) {
    println("started")
    println(s"To use JATOS type ${Common.getJatosHttpAddress}:${Common.getJatosHttpPort} in your " +
        s"browser's address bar")
  }

  lifecycle.addStopHook(() => Future {
    logger.info("JATOS shutdown")
    if (environment.isProd) println("JATOS stopped")
  })

}
