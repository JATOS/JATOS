package general.common;

import play.mvc.Http;

import java.io.File;

public class ResultBag extends play.mvc.Result {

    private final File file;

    public ResultBag(File file) {
        super(Http.Status.OK);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public boolean hasFile() {
        return file != null;
    }
}
