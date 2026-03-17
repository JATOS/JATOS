package controllers.gui;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Paths;

@Singleton
public class OpenApiController extends Controller {

    public Result openapi(Http.Request request) {
        File file = Paths.get( "jatos-api.yaml").toFile();
        if (file.exists()) {
            return ok(file).as("application/yaml");
        }
        return notFound("OpenAPI file not found");
    }
}
