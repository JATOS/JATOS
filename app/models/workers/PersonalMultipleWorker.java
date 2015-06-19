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
 * Worker for a Personal Multiple Run (worker for an external run that can be
 * used multiple times and always assigns the results to the same worker).
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(PersonalMultipleWorker.WORKER_TYPE)
public class PersonalMultipleWorker extends Worker {

	public static final String WORKER_TYPE = "PersonalMultiple";
	public static final String UI_WORKER_TYPE = "Personal Multiple";
	public static final String COMMENT = "comment";

	private String comment;

	public PersonalMultipleWorker() {
	}

	@JsonCreator
	public PersonalMultipleWorker(String comment) {
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
		List<ValidationError> errorList = new ArrayList<>();
		if (comment != null && !Jsoup.isValid(comment, Whitelist.none())) {
			errorList.add(new ValidationError(COMMENT,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		return errorList.isEmpty() ? null : errorList;
	}

}
