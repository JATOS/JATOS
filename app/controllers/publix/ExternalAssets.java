package controllers.publix;

import java.io.File;

import play.Play;
import play.mvc.Controller;
import play.mvc.Result;

public class ExternalAssets extends Controller {

	public static Result at(String path, String fileStr) {
		String basePath = Play.application().path().getPath();
		String fullPath = basePath + path + "/" + fileStr; 
		File file = new File(fullPath);
		if (file.exists() && !file.isDirectory()) {
			return ok(file, true);
		} else {
			return notFound(views.html.publix.error.render("Resource \""
					+ fileStr + "\" couldn't be found."));
		}
	}

}
