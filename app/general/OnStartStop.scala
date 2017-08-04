package general

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global

/**
  * Called during start-up
  *
  * @author Kristian Lange (2015)
  */
@Singleton
class OnStartStop @Inject()(lifecycle: ApplicationLifecycle) {

  private val logger = Logger(this.getClass)

  logger.info("JATOS has started")

  lifecycle.addStopHook(() => Future(logger.info("JATOS shutdown")))

}
