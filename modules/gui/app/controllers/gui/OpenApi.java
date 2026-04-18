package controllers.gui;

import general.common.Common;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class OpenApi extends Controller {

    public Result specs() {
        var path = Path.of(Common.getBasepath(),"jatos-api.yaml");
        if (Files.exists(path)) {
            try {
                return ok(Files.readString(path)).as("text/yaml");
            } catch (Exception e) {
                return internalServerError("Could not read OpenAPI file");
            }
        }
        return notFound("OpenAPI file not found");
    }
}
