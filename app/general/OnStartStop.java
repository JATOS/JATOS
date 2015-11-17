package general;

import javax.inject.Inject;
import javax.inject.Singleton;

import play.Logger;
import play.inject.ApplicationLifecycle;
import play.libs.F;

/**
 * @author Kristian Lange (2015)
 */
@Singleton
public class OnStartStop {

	private static final String CLASS_NAME = OnStartStop.class.getSimpleName();

	@Inject
	public OnStartStop(ApplicationLifecycle lifecycle) {
		Logger.info(CLASS_NAME + ": JATOS has started");
		
		lifecycle.addStopHook(() -> {
			Logger.info(CLASS_NAME + ": JATOS shutdown");
			return F.Promise.pure(null);
		});
	}

}
