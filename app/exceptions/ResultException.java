package exceptions;

import play.mvc.SimpleResult;

@SuppressWarnings("serial")
public class ResultException extends Exception {

	private SimpleResult result;
	
	public ResultException(SimpleResult result, String message) {
		super(message);
		this.result = result;
	}
	
	public ResultException(SimpleResult result) {
		super();
		this.result = result;
	}
	
	public SimpleResult getResult() {
		return result;
	}
	
}
