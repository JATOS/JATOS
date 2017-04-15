package session.batch;

import static akka.pattern.Patterns.ask;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import akka.util.Timeout;
import exceptions.publix.InternalServerErrorPublixException;
import models.common.Batch;
import models.common.StudyResult;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.LegacyWebSocket;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import session.DispatcherRegistryProtocol.Get;
import session.DispatcherRegistryProtocol.GetOrCreate;
import session.DispatcherRegistryProtocol.ItsThisOne;
import session.WebSocketBuilder;
import session.batch.akka.actors.BatchDispatcherProtocol.PoisonChannel;

/**
 * Service class that handles of opening and closing of batch channels with
 * Akka.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class BatchChannelService {

	private static final ALogger LOGGER = Logger.of(BatchChannelService.class);

	/**
	 * Time to wait for an answer after asking an Akka actor
	 */
	private static final Timeout TIMEOUT = new Timeout(
			Duration.create(5000l, "millis"));

	/**
	 * Akka Actor of the BatchDispatcherRegistry. It exists only one and it's
	 * created during startup of JATOS.
	 */
	@Inject
	@Named("batch-dispatcher-registry-actor")
	private ActorRef batchDispatcherRegistry;

	/**
	 * Opens a new batch channel WebSocket for the given StudyResult.
	 */
	public LegacyWebSocket<JsonNode> openBatchChannel(StudyResult studyResult)
			throws InternalServerErrorPublixException {
		Batch batch = studyResult.getBatch();
		// Get the BatchDispatcher that will handle this batch.
		ActorRef batchDispatcher = getOrCreateBatchDispatcher(batch);
		// If this BatchDispatcher already has a batch channel for this
		// StudyResult, close the old one before opening a new one.
		closeBatchChannel(studyResult, batchDispatcher);
		return WebSocketBuilder.withBatchChannel(studyResult.getId(),
				batchDispatcher);
	}

	/**
	 * Close the batch channel that belongs to the given StudyResult and batch.
	 * It just sends the closing message to the BatchDispatcher without waiting
	 * for an answer.
	 */
	public void closeBatchChannel(StudyResult studyResult, Batch batch)
			throws InternalServerErrorPublixException {
		if (batch == null) {
			return;
		}
		sendMsg(studyResult, batch, new PoisonChannel(studyResult.getId()));
	}

	private void sendMsg(StudyResult studyResult, Batch batch, Object msg)
			throws InternalServerErrorPublixException {
		ActorRef batchDispatcher = getBatchDispatcher(batch);
		if (batchDispatcher != null) {
			batchDispatcher.tell(msg, ActorRef.noSender());
		}
	}

	/**
	 * Get the BatchDispatcher to this batch.
	 * 
	 * @param batch
	 * @return ActorRef of the BatchDispatcher
	 * @throws InternalServerErrorPublixException
	 */
	private ActorRef getBatchDispatcher(Batch batch)
			throws InternalServerErrorPublixException {
		ItsThisOne answer = (ItsThisOne) askBatchDispatcherRegistry(
				new Get(batch.getId()));
		return answer.dispatcher;
	}

	/**
	 * Create a new BatchDispatcher to this Batch or get the already existing
	 * one.
	 * 
	 * @param batch
	 * @return ActorRef of the BatchDispatcher
	 * @throws InternalServerErrorPublixException
	 */
	private ActorRef getOrCreateBatchDispatcher(Batch batch)
			throws InternalServerErrorPublixException {
		ItsThisOne answer = (ItsThisOne) askBatchDispatcherRegistry(
				new GetOrCreate(batch.getId()));
		return answer.dispatcher;
	}

	/**
	 * Asks the BatchDispatcherRegistry. Waits until it receives an answer.
	 * 
	 * @param msg
	 *            Message Object to ask
	 * @return Answer Object
	 * @throws InternalServerErrorPublixException
	 */
	private Object askBatchDispatcherRegistry(Object msg)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(batchDispatcherRegistry, msg, TIMEOUT);
		try {
			return Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Closes the batch channel that belongs to the given StudyResult and is
	 * managed by the given BatchDispatcher. Waits until it receives a result
	 * from the BatchDispatcher actor.
	 * 
	 * @param studyResult
	 * @param batchDispatcher
	 * @return true if the BatchChannel was managed by the BatchDispatcher and
	 *         was successfully removed from the BatchDispatcher, false
	 *         otherwise (it was probably never managed by the dispatcher).
	 * @throws InternalServerErrorPublixException
	 */
	private boolean closeBatchChannel(StudyResult studyResult,
			ActorRef batchDispatcher)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(batchDispatcher,
				new PoisonChannel(studyResult.getId()), TIMEOUT);
		try {
			return (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

}
