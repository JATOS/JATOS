package batch

import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport
import batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne, Unregister}

import scala.collection.mutable

/**
  * A BatchDispatcherRegistry is an Akka Actor that keeps track of all
  * BatchDispatchers Actors.
  *
  * @author Kristian Lange (2017)
  */

object BatchDispatcherRegistry {

  abstract class RegistryProtocol

  case class ItsThisOne(dispatcher: ActorRef) extends RegistryProtocol

  case class GetOrCreate(batchId: Long) extends RegistryProtocol

  case class Unregister(batchId: Long) extends RegistryProtocol

}

@Singleton
class BatchDispatcherRegistry @Inject()(actorSystem: ActorSystem,
                                        batchDispatcherFactory: BatchDispatcher.Factory,
                                        batchActionHandler: BatchActionHandler,
                                        batchActionMsgBuilder: BatchActionMsgBuilder)
  extends Actor with InjectedActorSupport {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Contains the dispatchers that are currently registered. Maps the an ID to
    * the ActorRef.
    */
  private val dispatcherMap = mutable.HashMap[Long, ActorRef]()

  def receive = {
    case GetOrCreate(batchId: Long) =>
      // Someone wants to know the Dispatcher to a particular ID
      // If it doesn't exist, create a new one.
      if (!dispatcherMap.contains(batchId)) {
        val dispatcher = injectedChild(
          batchDispatcherFactory(self, batchActionHandler, batchActionMsgBuilder, batchId),
          batchId.toString)
        dispatcherMap += (batchId -> dispatcher)
        logger.debug(s".receive: registered dispatcher for batch ID $batchId")
      }
      sender() ! ItsThisOne(dispatcherMap(batchId))
    case Unregister(batchId: Long) =>
      // A Dispatcher closed down and wants to unregister
      dispatcherMap -= batchId
      logger.debug(s".receive: unregistered dispatcher for batch ID $batchId")
  }

}
