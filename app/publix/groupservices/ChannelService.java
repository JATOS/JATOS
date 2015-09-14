package publix.groupservices;

import static akka.pattern.Patterns.ask;
import models.GroupModel;
import models.StudyResult;
import play.mvc.WebSocket;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.InternalServerErrorPublixException;
import publix.exceptions.NotFoundPublixException;
import publix.groupservices.akka.actors.GroupDispatcherRegistry;
import publix.groupservices.akka.messages.Get;
import publix.groupservices.akka.messages.GetOrCreate;
import publix.groupservices.akka.messages.ItsThisOne;
import publix.groupservices.akka.messages.PoisonSomeone;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.util.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import common.Common;
import common.Global;

@Singleton
public class ChannelService {

	public static final Timeout TIMEOUT = new Timeout(Duration.create(
			Common.GROUP_CHANNEL_TIMEOUT, "millis"));

	private final ActorRef GROUP_DISPATCHER_REGISTRY = Global.INJECTOR
			.getInstance(Key.get(ActorRef.class,
					Names.named(GroupDispatcherRegistry.ACTOR_NAME)));

	/**
	 * Opens a new group channel for the given StudyResult.
	 */
	public WebSocket<JsonNode> openGroupChannel(StudyResult studyResult)
			throws InternalServerErrorPublixException,
			ForbiddenPublixException, NotFoundPublixException {
		// Get the GroupDispatcher that will handle this Group. Create a
		// new GroupDispatcher or get the already existing one.
		ActorRef groupDispatcher = getOrCreateGroupDispatcher(studyResult
				.getGroup());
		// If this GroupDispatcher already has a group channel for this
		// StudyResult, close the old one before opening a new one.
		closeGroupChannel(studyResult, groupDispatcher);
		return WebSocketBuilder.withGroupChannel(studyResult.getId(),
				groupDispatcher);
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
		Future<Object> future = ask(groupDispatcher, new PoisonSomeone(
				studyResult.getId()), TIMEOUT);
		try {
			return (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
	}

	/**
	 * Close the group channel that belongs to the given StudyResult and
	 * group. It just sends the closing message to the GroupDispatcher
	 * without waiting for an answer. We take a separate group and not the
	 * StudyResult's group because the StudyResult's group might
	 * already be assigned a null value during leaving of a group.
	 */
	public void closeGroupChannel(StudyResult studyResult,
			GroupModel group) throws InternalServerErrorPublixException {
		if (group == null) {
			return;
		}
		ActorRef groupDispatcher = getGroupDispatcher(group);
		if (groupDispatcher != null) {
			groupDispatcher.tell(new PoisonSomeone(studyResult.getId()),
					ActorRef.noSender());
		}
	}

	private ActorRef getGroupDispatcher(GroupModel group)
			throws InternalServerErrorPublixException {
		return retrieveGroupDispatcher(new Get(group.getId()));
	}

	private ActorRef getOrCreateGroupDispatcher(GroupModel group)
			throws InternalServerErrorPublixException {
		return retrieveGroupDispatcher(new GetOrCreate(group.getId()));
	}

	/**
	 * Asks the GroupDispatcherRegistry for a GroupDispatcher, waits until it
	 * receives an answer and returns the received GroupDispatcher.
	 * 
	 * @param msg
	 *            Either a Get or a GetOrCreate message object.
	 * @return The ActorRef of the GroupDispatcher
	 * @throws InternalServerErrorPublixException
	 */
	private ActorRef retrieveGroupDispatcher(Object msg)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(GROUP_DISPATCHER_REGISTRY, msg, TIMEOUT);
		Object answer;
		try {
			answer = Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
		return ((ItsThisOne) answer).groupDispatcher;
	}

}
