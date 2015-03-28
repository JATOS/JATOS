package exceptions.gui;

import controllers.gui.actionannotations.JatosGuiAction;
import play.mvc.SimpleResult;

/**
 * Exception for any kind of exceptional behaviour within one of JATOS' GUI
 * actions. A SimpleResult is defined that will be displayed instead of the
 * normal action's output. All JatosGuiExceptions are caught by
 * {@link JatosGuiAction} and the {@link JatosGui} annotation.
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class JatosGuiException extends Exception {

	private SimpleResult simpleResult;

	public JatosGuiException(SimpleResult result, String message) {
		super(message);
		this.simpleResult = result;
	}

	public JatosGuiException(SimpleResult result) {
		super();
		this.simpleResult = result;
	}

	public SimpleResult getSimpleResult() {
		return simpleResult;
	}

}
