package models.common.workers;

import com.fasterxml.jackson.annotation.JsonCreator;
import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.ValidationError;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;

import static models.common.workers.WorkerType.PERSONAL_MULTIPLE;

/**
 * DB entity of the concrete Worker for a Personal Multiple Run (worker for an external run that can be used multiple
 * times and always assigns the results to the same worker).
 */
@Entity
@DiscriminatorValue(PersonalMultipleWorker.WORKER_TYPE)
public class PersonalMultipleWorker extends Worker {

    static final String WORKER_TYPE = "PersonalMultiple";
    static final String SHORT_WORKER_TYPE = "pm";
    static final String UI_WORKER_TYPE = "Personal Multiple";

    public PersonalMultipleWorker() {
    }

    @JsonCreator
    public PersonalMultipleWorker(String comment) {
        this.comment = comment;
    }

    @Override
    public WorkerType getWorkerType() {
        return PERSONAL_MULTIPLE;
    }

    @Override
    public String generateConfirmationCode() {
        return null;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (comment != null && comment.length() > 255) {
            errorList.add(new ValidationError(COMMENT, MessagesStrings.COMMENT_TOO_LONG));
        }
        if (comment != null && !Jsoup.isValid(comment, Safelist.none())) {
            errorList.add(new ValidationError(COMMENT, MessagesStrings.NO_HTML_ALLOWED));
        }
        return errorList.isEmpty() ? null : errorList;
    }

}
