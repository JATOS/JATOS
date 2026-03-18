package models.gui;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;
import services.gui.WorkerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Properties of a study code request + validation rules
 *
 * @author Kristian Lange
 */
@Constraints.Validate
public class StudyCodeProperties implements Validatable<List<ValidationError>> {

    public static final String COMMENT = "comment";
    public static final String TYPE = "type";
    public static final String AMOUNT = "amount";

    private String type;

    private String comment;

    private int amount;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getComment() {
        return this.comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();

        if (type == null) {
            errorList.add(new ValidationError(TYPE, "Invalid type"));
        }

        if (comment != null && comment.length() > 255) {
            errorList.add(new ValidationError(COMMENT, "Comment too long"));
        }
        if (comment != null && !Jsoup.isValid(comment, Safelist.none())) {
            errorList.add(new ValidationError(COMMENT, "No HTML allowed"));
        }

        if (amount <= 0 || amount > 1000) {
            errorList.add(new ValidationError(AMOUNT, "Amount must be > 0 and <= 1000"));
        }

        return errorList.isEmpty() ? null : errorList;
    }

}
