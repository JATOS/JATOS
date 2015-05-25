package models.workers;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;
import services.gui.MessagesStrings;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Worker if a study is run as personal single worker. This kind of worker is
 * for an external run. The worker can run the study only once. The worker is
 * created by a user before the study is started.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(PersonalSingleWorker.WORKER_TYPE)
public class PersonalSingleWorker extends Worker {

	public static final String WORKER_TYPE = "ClosedStandalone";
	public static final String UI_WORKER_TYPE = "Personal Single";
	public static final String COMMENT = "comment";

	private String comment;

	public PersonalSingleWorker() {
	}

	@JsonCreator
	public PersonalSingleWorker(String comment) {
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
	public String toString() {
		return comment + ", " + super.toString();
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
