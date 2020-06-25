package controllers.gui;

import controllers.AssetsMetadata;
import play.api.http.HttpErrorHandler;
import play.api.mvc.Action;
import play.api.mvc.AnyContent;

import javax.inject.Inject;

public class Assets extends controllers.Assets {

    @Inject
    public Assets(HttpErrorHandler errorHandler, AssetsMetadata meta) {
        super(errorHandler, meta);
    }

    public Action<AnyContent> at(String path, String file) {
        boolean aggressiveCaching = true;
        return super.at(path, file, aggressiveCaching);
    }

    public Action<AnyContent> versioned(String path, controllers.Assets.Asset file) {
        return super.versioned(path, file);
    }
}
