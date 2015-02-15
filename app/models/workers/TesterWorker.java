package models.workers;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;
import services.MessagesStrings;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Worker for testing purpose.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(TesterWorker.WORKER_TYPE)
public class TesterWorker extends Worker {

	public static final String WORKER_TYPE = "Tester";
	public static final String UI_WORKER_TYPE = "Tester";
	public static final String COMMENT = "comment";

	private String comment;
	
	public TesterWorker() {
	}
	
	@JsonCreator
	public TesterWorker(String comment) {
		this.comment = comment;
	}
	
	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getComment() {
		return this.comment;
	}
	
	public String getWorkerType() {
		return WORKER_TYPE;
	}
	
	public String getUIWorkerType() {
		return UI_WORKER_TYPE;
	}

	@Override
	public String generateConfirmationCode() {
		return null;
	}
	
	@Override
	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (comment != null && !Jsoup.isValid(comment, Whitelist.none())) {
			errorList.add(new ValidationError(COMMENT,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		return errorList.isEmpty() ? null : errorList;
	}

}
