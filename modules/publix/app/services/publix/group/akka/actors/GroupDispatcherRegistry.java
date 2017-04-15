package services.publix.group.akka.actors;

import javax.inject.Inject;
import javax.inject.Singleton;

import akka.actor.ActorSystem;
import akka.actor.Props;
import services.publix.group.akka.actors.services.GroupActionHandler;
import services.publix.group.akka.actors.services.GroupActionMsgBuilder;
import session.DispatcherRegistry;

/**
 * A GroupDispatcherRegistry is an Akka Actor keeps track of all
 * GroupDispatchers Actors.
 * 
 * @author Kristian Lange (2015, 2017)
 */
@Singleton
public class GroupDispatcherRegistry extends DispatcherRegistry {

	private final GroupActionHandler groupActionHandler;
	private final GroupActionMsgBuilder groupActionMsgBuilder;

	@Inject
	public GroupDispatcherRegistry(ActorSystem actorSystem,
			GroupActionHandler groupActionHandler,
			GroupActionMsgBuilder groupActionMsgBuilder) {
		super(actorSystem);
		this.groupActionHandler = groupActionHandler;
		this.groupActionMsgBuilder = groupActionMsgBuilder;
	}

	@Override
	protected Props getProps(long groupResultId) {
		return GroupDispatcher.props(self(), groupActionHandler,
				groupActionMsgBuilder, groupResultId);
	}

}