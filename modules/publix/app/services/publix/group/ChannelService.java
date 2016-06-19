package services.publix.group;

import static akka.pattern.Patterns.ask;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import akka.util.Timeout;
import exceptions.publix.InternalServerErrorPublixException;
import models.common.GroupResult;
import models.common.StudyResult;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.WebSocket;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import services.publix.group.akka.messages.GroupDispatcherProtocol.Joined;
import services.publix.group.akka.messages.GroupDispatcherProtocol.Left;
import services.publix.group.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import services.publix.group.akka.messages.GroupDispatcherProtocol.ReassignChannel;
import services.publix.group.akka.messages.GroupDispatcherRegistryProtocol.Get;
import services.publix.group.akka.messages.GroupDispatcherRegistryProtocol.GetOrCreate;
import services.publix.group.akka.messages.GroupDispatcherRegistryProtocol.ItsThisOne;

/**
 * Service class that handles of opening and closing of group channels with
 * Akka.
 * 
 * @author Kristian Lange (2015)
 */
@Singleton
public class ChannelService {

	private static final ALogger LOGGER = Logger.of(ChannelService.class);

	/**
	 * Time to wait for an answer after asking an Akka actor
	 */
	private static final Timeout TIMEOUT = new Timeout(
			Duration.create(5000l, "millis"));

	/**
	 * Akka Actor of the GroupDispatcherRegistry. It exists only one and it's
	 * created during startup of JATOS.
	 */
	@Inject
	@Named("group-dispatcher-registry-actor")
	private ActorRef groupDispatcherRegistry;

	/**
	 * Opens a new group channel WebSocket for the given StudyResult.
	 */
	public WebSocket<JsonNode> openGroupChannel(StudyResult studyResult)
			throws InternalServerErrorPublixException {
		GroupResult groupResult = studyResult.getActiveGroupResult();
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
	 * Reassigns the given group channel that is associated with the given
	 * StudyResult. It moves the group channel from the current GroupDispatcher
	 * to a different one that is associated with the given GroupResult.
	 */
	public void reassignGroupChannel(StudyResult studyResult,
			GroupResult currentGroupResult, GroupResult differentGroupResult)
			throws InternalServerErrorPublixException {
		ActorRef currentGroupDispatcher = getGroupDispatcher(
				currentGroupResult);
		// Get or create, because if the dispatcher was empty it was shutdown
		// and has to be recreated
		ActorRef differentGroupDispatcher = getOrCreateGroupDispatcher(
				differentGroupResult);
		if (currentGroupDispatcher == null
				|| differentGroupDispatcher == null) {
			LOGGER.error(
					".reassignGroupChannel: couldn't reassign group channel "
							+ "from current group result "
							+ currentGroupResult.getId()
							+ " to different group result "
							+ differentGroupResult.getId()
							+ ". One of the dispatchers doesn't exist.");
			throw new InternalServerErrorPublixException(
					"Couldn't reassign group channel.");
		}

		currentGroupDispatcher.tell(new ReassignChannel(studyResult.getId(),
				differentGroupDispatcher), ActorRef.noSender());
		currentGroupDispatcher.tell(new Left(studyResult.getId()),
				ActorRef.noSender());
		differentGroupDispatcher.tell(new Joined(studyResult.getId()),
				ActorRef.noSender());
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
		GroupResult groupResult = studyResult.getActiveGroupResult();
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
		ItsThisOne answer = (ItsThisOne) askGroupDispatcherRegistry(
				new Get(groupResult.getId()));
		return answer.groupDispatcher;
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
		ItsThisOne answer = (ItsThisOne) askGroupDispatcherRegistry(
				new GetOrCreate(groupResult.getId()));
		return answer.groupDispatcher;
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
			ActorRef groupDispatcher)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(groupDispatcher,
				new PoisonChannel(studyResult.getId()), TIMEOUT);
		try {
			return (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

}
