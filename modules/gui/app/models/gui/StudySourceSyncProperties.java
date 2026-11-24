package models.gui;

import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.util.List;

/**
 * Selected overwrite scope for synchronization.
 */
@Constraints.Validate
public class StudySourceSyncProperties implements Validatable<List<ValidationError>> {

    /**
     * Replace study and component properties.
     */
    private boolean properties;

    /**
     * Completely replace the study assets directory.
     */
    private boolean assets;

    public boolean isProperties() {
        return properties;
    }

    public void setProperties(boolean properties) {
        this.properties = properties;
    }

    public boolean isAssets() {
        return assets;
    }

    public void setAssets(boolean assets) {
        this.assets = assets;
    }

    @Override
    public List<ValidationError> validate() {
        if (!properties && !assets)
            return List.of(new ValidationError("properties", "Select properties, assets, or both."));
        return null;
    }
}
