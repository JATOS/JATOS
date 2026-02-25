package models.gui;

import com.google.common.base.Strings;
import general.common.MessagesStrings;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import utils.common.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kristian Lange (2017)
 */
@Constraints.Validate
public class BatchOrGroupSession implements Constraints.Validatable<List<ValidationError>> {

    public static final String VERSION = "version";
    public static final String SESSION_DATA = "sessionData";

    private Long version;

    private String sessionData;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getSessionData() {
        return sessionData;
    }

    public void setSessionData(String sessionData) {
        this.sessionData = sessionData;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (!Strings.isNullOrEmpty(sessionData) && !JsonUtils.isValid(sessionData)) {
            errorList.add(new ValidationError(SESSION_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        return errorList.isEmpty() ? null : errorList;
    }

}
