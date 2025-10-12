package group

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy}
import group.GroupDispatcherRegistry.{Get, GetOrCreate, ItsThisOne, Unregister}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * A GroupDispatcherRegistry is an Akka Actor keeps track of all
  * GroupDispatchers Actors.
  *
  * @author Kristian Lange (2015, 2017)
  */
object GroupDispatcherRegistry {

  abstract class RegistryProtocol

  /**
    * Used by the GroupChannel service to ask which GroupDispatcher actor manages a particular
    * group (specified by the group result ID).
    */
  case class Get(groupResultId: Long) extends RegistryProtocol

  /**
    * Used by the GroupChannel service to ask which GroupDispatcher actor manages a particular
    * group (specified by the group result ID). If it doesn't exist, create a new one.
    */
  case class GetOrCreate(groupResultId: Long) extends RegistryProtocol

  /**
    * Used to answer the GroupChannel service which GroupDispatcher manages a particular group.
    */
  case class ItsThisOne(groupDispatcherOption: Option[ActorRef]) extends RegistryProtocol

  /**
    * Used by a GroupDispatcher to unregister itself from this registry
    */
  case class Unregister(groupResultId: Long) extends RegistryProtocol

}

@Singleton
class GroupDispatcherRegistry @Inject()(actorSystem: ActorSystem,
                                        dispatcherFactory: GroupDispatcher.Factory,
                                        actionHandler: GroupActionHandler,
                                        actionMsgBuilder: GroupActionMsgBuilder)
    extends Actor with InjectedActorSupport {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Override this Actor's supervisor strategy: in case of an Exception, resume a child actor without
    * stopping. This means that even if a GroupDispatcher throws an Exception, it continues
    * running and keeps its internal state (incl registered channels).
    */
  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute, loggingEnabled = true) {
      case _: Exception => Resume
    }

  /**
    * Contains the dispatchers that are currently registered. Maps an ID to the ActorRef.
    */
  private val dispatcherMap = mutable.HashMap[Long, ActorRef]()

  def receive: PartialFunction[Any, Unit] = {
    case Get(groupResultId: Long) =>
      // Someone wants to know the Dispatcher to a certain ID
      sender ! ItsThisOne(dispatcherMap.get(groupResultId))
    case GetOrCreate(groupResultId: Long) =>
      // Someone wants to know the Dispatcher to a particular ID
      // If it doesn't exist, create a new one.
      if (!dispatcherMap.contains(groupResultId)) {
        val dispatcher = injectedChild(
          dispatcherFactory(self, actionHandler, actionMsgBuilder, groupResultId),
          groupResultId.toString)
        dispatcherMap += (groupResultId -> dispatcher)
        logger.debug(s".receive: registered dispatcher for groupResult ID $groupResultId")
      }
      sender ! ItsThisOne(dispatcherMap.get(groupResultId))
    case Unregister(groupResultId: Long) =>
      // A Dispatcher closed down and wants to unregister
      dispatcherMap -= groupResultId
      logger.debug(s".receive: unregistered dispatcher for groupResult ID $groupResultId")
  }
}
