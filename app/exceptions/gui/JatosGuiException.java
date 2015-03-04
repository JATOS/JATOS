package exceptions.gui;

import controllers.gui.JatosGuiAction;
import play.mvc.Result;

/**
 * Exception for any kind of exceptional behaviour within one of JATOS' GUI
 * actions. A Result is defined that will be displayed instead of the
 * normal action's output. All JatosGuiExceptions are caught by
 * {@link JatosGuiAction}.
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class JatosGuiException extends Exception {

	private Result simpleResult;

	public JatosGuiException(Result result, String message) {
		super(message);
		this.simpleResult = result;
	}

	public JatosGuiException(Result result) {
		super();
		this.simpleResult = result;
	}

	public Result getSimpleResult() {
		return simpleResult;
	}

}
