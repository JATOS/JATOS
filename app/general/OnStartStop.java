package general;

import javax.inject.Inject;

import play.Logger;
import play.inject.ApplicationLifecycle;
import play.libs.F;

/**
 * @author Kristian Lange (2015)
 */
public class OnStartStop {

	private static final String CLASS_NAME = OnStartStop.class.getSimpleName();

	@Inject
	public OnStartStop(ApplicationLifecycle lifecycle, Initializer initializer) {
		Logger.info(CLASS_NAME + ": JATOS has started");
//		initializer.initialize();
		
		lifecycle.addStopHook(() -> {
			Logger.info(CLASS_NAME + ": JATOS shutdown");
			return F.Promise.pure(null);
		});
	}

}
