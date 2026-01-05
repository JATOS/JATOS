package general.common;

import akka.actor.ActorSystem;
import play.api.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

/**
 * Custom execution context for file system io operations.
 * Prevents blocking operations from exhausting Play's default thread pool.
 */
public class StudyAssetsExecutor extends CustomExecutionContext {

    @Inject
    public StudyAssetsExecutor(ActorSystem actorSystem) {
        super(actorSystem, "studyassets.dispatcher");
    }
}
