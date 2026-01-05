package general.common;

import akka.actor.ActorSystem;
import play.api.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

/**
 * Custom execution context for database operations.
 * Prevents blocking operations from exhausting Play's default thread pool.
 */
public class IOExecutor extends CustomExecutionContext {

    @Inject
    public IOExecutor(ActorSystem actorSystem) {
        super(actorSystem, "io.dispatcher");
    }
}
