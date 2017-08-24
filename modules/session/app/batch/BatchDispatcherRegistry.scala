package batch

import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem}
import batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne, Unregister}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

import scala.collection.mutable

/**
  * A BatchDispatcherRegistry is an Akka Actor that keeps track of all BatchDispatcher Actors.
  *
  * @author Kristian Lange (2017)
  */
object BatchDispatcherRegistry {

  abstract class RegistryProtocol

  /**
    * Used by the BatchChannel service to ask which BatchDispatcher actor manages a particular
    * batch. If it doesn't exist, create a new one.
    */
  case class GetOrCreate(batchId: Long) extends RegistryProtocol

  /**
    * Used to answer the BatchChannel service which BatchDispatcher actor manages a particular
    * batch.
    */
  case class ItsThisOne(dispatcher: ActorRef) extends RegistryProtocol

  /**
    * Used by a BatchDispatcher to unregister itself from this registry
    */
  case class Unregister(batchId: Long) extends RegistryProtocol

}

@Singleton
class BatchDispatcherRegistry @Inject()(actorSystem: ActorSystem,
                                        dispatcherFactory: BatchDispatcher.Factory,
                                        actionHandler: BatchActionHandler,
                                        actionMsgBuilder: BatchActionMsgBuilder)
  extends Actor with InjectedActorSupport {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Contains the dispatchers that are currently registered. Maps the an ID to the ActorRef.
    */
  private val dispatcherMap = mutable.HashMap[Long, ActorRef]()

  def receive = {
    case GetOrCreate(batchId: Long) =>
      // Someone wants to know the Dispatcher to a particular ID
      // If it doesn't exist, create a new one.
      if (!dispatcherMap.contains(batchId)) {
        val dispatcher = injectedChild(
          dispatcherFactory(self, actionHandler, actionMsgBuilder, batchId), batchId.toString)
        dispatcherMap += (batchId -> dispatcher)
        logger.debug(s".receive: registered dispatcher for batch ID $batchId")
      }
      sender ! ItsThisOne(dispatcherMap(batchId))
    case Unregister(batchId: Long) =>
      dispatcherMap -= batchId
      logger.debug(s".receive: unregistered dispatcher for batch ID $batchId")
  }

}
