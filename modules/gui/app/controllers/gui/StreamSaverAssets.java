package controllers.gui;

import controllers.AssetsMetadata;
import play.api.http.HttpErrorHandler;
import play.api.mvc.Action;
import play.api.mvc.AnyContent;

import javax.inject.Inject;

/**
 * Needed for StreamSaver.js to work with URL base path. StreamSaver loads mitm.html and sw.js through here.
 */
public class StreamSaverAssets extends controllers.Assets {

    @Inject
    public StreamSaverAssets(HttpErrorHandler errorHandler, AssetsMetadata meta) {
        super(errorHandler, meta);
    }

    public Action<AnyContent> at1(String path, String file, Long... ignore) {
        boolean aggressiveCaching = true;
        return super.at(path, file, aggressiveCaching);
    }

    public Action<AnyContent> at2(String path, String file, Long... ignore) {
        boolean aggressiveCaching = true;
        return super.at(path, file, aggressiveCaching);
    }
}
