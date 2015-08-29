package publix.services;

import static akka.pattern.Patterns.ask;
import models.GroupResult;
import models.StudyModel;
import models.StudyResult;
import models.workers.Worker;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.WebSocket;
import publix.akka.actors.GroupDispatcherRegistry;
import publix.akka.messages.Get;
import publix.akka.messages.GetOrCreate;
import publix.akka.messages.IsMember;
import publix.akka.messages.ItsThisOne;
import publix.akka.messages.PoisonSomeone;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.InternalServerErrorPublixException;
import publix.exceptions.NotFoundPublixException;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.util.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ChannelService<T extends Worker> {

	private static final Timeout TIMEOUT = new Timeout(Duration.create(1000,
			"seconds"));
	private static final ActorRef GROUP_DISPATCHER_REGISTRY = Akka.system()
			.actorOf(GroupDispatcherRegistry.props());

	private final PublixUtils<T> publixUtils;
	private final IStudyAuthorisation<T> studyAuthorisation;
	private final PublixErrorMessages errorMessages;

	@Inject
	public ChannelService(PublixUtils<T> publixUtils,
			IStudyAuthorisation<T> studyAuthorisation,
			PublixErrorMessages errorMessages) {
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.errorMessages = errorMessages;

	}

	public WebSocket<JsonNode> openGroupChannel(Long studyId, String workerIdStr)
			throws InternalServerErrorPublixException,
			ForbiddenPublixException, NotFoundPublixException {
		T worker = publixUtils.retrieveTypedWorker(workerIdStr);
		StudyModel study = publixUtils.retrieveStudy(studyId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			throw new ForbiddenPublixException(
					errorMessages.workerDidntJoinGroup(worker, study.getId()));
		}
		// Get the GroupDispatcher that will handle this GroupResult. Create a
		// new one or get the already existing one.
		ActorRef groupDispatcher = retrieveGroupDispatcher(new GetOrCreate(
				groupResult.getId()));
		if (isMemberOfGroup(studyResult, groupDispatcher)) {
			// This studyResult is already member of a group
			return WebSocketBuilder.reject(Controller.badRequest());
		}
		return WebSocketBuilder.withGroupChannelActor(studyResult.getId(),
				groupDispatcher);
	}

	public void closeGroupChannel(StudyResult studyResult,
			GroupResult groupResult) throws InternalServerErrorPublixException {
		if (groupResult == null) {
			return;
		}
		ActorRef groupDispatcher = retrieveGroupDispatcher(new Get(
				groupResult.getId()));
		if (groupDispatcher != null) {
			groupDispatcher.tell(new PoisonSomeone(studyResult.getId()),
					ActorRef.noSender());
		}
	}

	private ActorRef retrieveGroupDispatcher(Object msg)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(GROUP_DISPATCHER_REGISTRY, msg, TIMEOUT);
		Object answer;
		try {
			answer = Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
		return ((ItsThisOne) answer).channel;
	}

	private boolean isMemberOfGroup(StudyResult studyResult,
			ActorRef groupDispatcher) throws InternalServerErrorPublixException {
		Future<Object> future = ask(groupDispatcher,
				new IsMember(studyResult.getId()), TIMEOUT);
		boolean result;
		try {
			result = (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
		return result;
	}

}
