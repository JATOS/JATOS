package controllers.gui;

import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Paths;

@Singleton
public class OpenApiController extends Controller {

    public Result openapi() {
        var path = Paths.get("jatos-api.yaml");
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
