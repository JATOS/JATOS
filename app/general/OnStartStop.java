package general;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import play.Logger;
import play.Logger.ALogger;
import play.inject.ApplicationLifecycle;

/**
 * @author Kristian Lange (2015)
 */
@Singleton
public class OnStartStop {

	private static final ALogger LOGGER = Logger.of(OnStartStop.class);

	@Inject
	public OnStartStop(ApplicationLifecycle lifecycle) {
		LOGGER.info("JATOS has started");

		lifecycle.addStopHook(() -> {
			LOGGER.info("JATOS shutdown");
			return CompletableFuture.completedFuture(null);
		});
	}

}
