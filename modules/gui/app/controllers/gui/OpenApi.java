package controllers.gui;

import general.common.Common;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Singleton
public class OpenApi extends Controller {

    public Result specs() {
        var path = Paths.get(Common.getBasepath() + File.separator + "jatos-api.yaml");
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
