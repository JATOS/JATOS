package publix.groupservices;

import static akka.pattern.Patterns.ask;

import javax.inject.Singleton;

import models.GroupResult;
import models.StudyResult;
import play.mvc.WebSocket;
import publix.exceptions.InternalServerErrorPublixException;
import publix.groupservices.akka.actors.GroupDispatcherRegistry;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.Joined;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.Left;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.Get;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.GetOrCreate;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.ItsThisOne;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.util.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Key;
import com.google.inject.name.Names;
import common.Global;

/**
 * Service class that handles of opening and closing of group channels with
 * Akka.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class ChannelService {

	/**
	 * Time to wait for an answer after asking a Akka actor
	 */
	private static final Timeout TIMEOUT = new Timeout(Duration.create(5000l,
			"millis"));

	/**
	 * Akka Actor of the GroupDispatcherRegistry. It exists only one and it's
	 * created during startup of JATOS.
	 */
	private final ActorRef groupDispatcherRegistry = Global.INJECTOR
			.getInstance(Key.get(ActorRef.class,
					Names.named(GroupDispatcherRegistry.ACTOR_NAME)));

	/**
	 * Opens a new group channel WebSocket for the given StudyResult.
	 */
	public WebSocket<JsonNode> openGroupChannel(StudyResult studyResult)
			throws InternalServerErrorPublixException {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			return null;
		}
		// Get the GroupDispatcher that will handle this GroupResult.
		ActorRef groupDispatcher = getOrCreateGroupDispatcher(groupResult);
		// If this GroupDispatcher already has a group channel for this
		// StudyResult, close the old one before opening a new one.
		closeGroupChannel(studyResult, groupDispatcher);
		return WebSocketBuilder.withGroupChannel(studyResult.getId(),
				groupDispatcher);
	}

	/**
	 * Close the group channel that belongs to the given StudyResult and
	 * GroupResult. It just sends the closing message to the GroupDispatcher
	 * without waiting for an answer. We don't use the StudyResult's GroupResult
	 * but ask for a separate parameter for the GroupResult because the
	 * StudyResult's GroupResult might already be null in the process of leaving
	 * a GroupResult.
	 */
	public void closeGroupChannel(StudyResult studyResult,
			GroupResult groupResult) throws InternalServerErrorPublixException {
		if (groupResult == null) {
			return;
		}
		sendMsg(studyResult, groupResult,
				new PoisonChannel(studyResult.getId()));
	}

	/**
	 * Sends a message to each member of the group (the GroupResult this
	 * studyResult is in). This message tells that this member has joined the
	 * GroupResult.
	 */
	public void sendJoinedMsg(StudyResult studyResult)
			throws InternalServerErrorPublixException {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult != null) {
			sendMsg(studyResult, groupResult, new Joined(studyResult.getId()));
		}
	}

	/**
	 * Sends a message to each member of the GroupResult that this member
	 * (specified by StudyResult) has left the GroupResult.
	 */
	public void sendLeftMsg(StudyResult studyResult, GroupResult groupResult)
			throws InternalServerErrorPublixException {
		if (groupResult != null) {
			sendMsg(studyResult, groupResult, new Left(studyResult.getId()));
		}
	}

	private void sendMsg(StudyResult studyResult, GroupResult groupResult,
			Object msg) throws InternalServerErrorPublixException {
		ActorRef groupDispatcher = getGroupDispatcher(groupResult);
		if (groupDispatcher != null) {
			groupDispatcher.tell(msg, ActorRef.noSender());
		}
	}

	/**
	 * Get the GroupDispatcher to this GroupResult.
	 * 
	 * @param groupResult
	 * @return ActorRef of the GroupDispatcher
	 * @throws InternalServerErrorPublixException
	 */
	private ActorRef getGroupDispatcher(GroupResult groupResult)
			throws InternalServerErrorPublixException {
		Object answer = askGroupDispatcherRegistry(new Get(groupResult.getId()));
		return ((ItsThisOne) answer).groupDispatcher;
	}

	/**
	 * Create a new GroupDispatcher to this GroupResult or get the already
	 * existing one.
	 * 
	 * @param groupResult
	 * @return ActorRef of the GroupDispatcher
	 * @throws InternalServerErrorPublixException
	 */
	private ActorRef getOrCreateGroupDispatcher(GroupResult groupResult)
			throws InternalServerErrorPublixException {
		Object answer = askGroupDispatcherRegistry(new GetOrCreate(
				groupResult.getId()));
		return ((ItsThisOne) answer).groupDispatcher;
	}

	/**
	 * Asks the GroupDispatcherRegistry. Waits until it receives an answer.
	 * 
	 * @param msg
	 *            Message Object to ask
	 * @return Answer Object
	 * @throws InternalServerErrorPublixException
	 */
	private Object askGroupDispatcherRegistry(Object msg)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(groupDispatcherRegistry, msg, TIMEOUT);
		try {
			return Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Closes the group channel that belongs to the given StudyResult and is
	 * managed by the given GroupDispatcher. Waits until it receives a result
	 * from the GroupDispatcher actor.
	 * 
	 * @param studyResult
	 * @param groupDispatcher
	 * @return true if the GroupChannel was managed by the GroupDispatcher and
	 *         was successfully removed from the GroupDispatcher, false
	 *         otherwise (it was probably never managed by the dispatcher).
	 * @throws InternalServerErrorPublixException
	 */
	private boolean closeGroupChannel(StudyResult studyResult,
			ActorRef groupDispatcher) throws InternalServerErrorPublixException {
		Future<Object> future = ask(groupDispatcher, new PoisonChannel(
				studyResult.getId()), TIMEOUT);
		try {
			return (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

}
