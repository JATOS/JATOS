package session.batch.akka.actors;

import javax.inject.Inject;
import javax.inject.Singleton;

import akka.actor.ActorSystem;
import akka.actor.Props;
import session.DispatcherRegistry;

/**
 * A BatchDispatcherRegistry is an Akka Actor keeps track of all
 * BatchDispatchers Actors.
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class BatchDispatcherRegistry extends DispatcherRegistry {

	private final BatchActionHandler batchActionHandler;
	private final BatchActionMsgBuilder batchActionMsgBuilder;

	@Inject
	public BatchDispatcherRegistry(ActorSystem actorSystem,
			BatchActionHandler batchActionHandler,
			BatchActionMsgBuilder batchActionMsgBuilder) {
		super(actorSystem);
		this.batchActionHandler = batchActionHandler;
		this.batchActionMsgBuilder = batchActionMsgBuilder;
	}

	@Override
	protected Props getProps(long batchId) {
		return BatchDispatcher.props(self(), batchActionHandler,
				batchActionMsgBuilder, batchId);
	}

}