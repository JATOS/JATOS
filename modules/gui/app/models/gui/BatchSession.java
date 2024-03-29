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
public class BatchSession implements Constraints.Validatable<List<ValidationError>> {

    public static final String VERSION = "version";
    public static final String DATA = "data";

    private Long version;

    private String data;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (!Strings.isNullOrEmpty(data) && !JsonUtils.isValid(data)) {
            errorList.add(new ValidationError(DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        return errorList.isEmpty() ? null : errorList;
    }

}
