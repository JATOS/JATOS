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
	
	public ResultException(String message) {
		super(message);
	}
	
	public SimpleResult getResult() {
		return result;
	}
	
	public void setResult(SimpleResult result) {
		this.result =  result;
	}

}
