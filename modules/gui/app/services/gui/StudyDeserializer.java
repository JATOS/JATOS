package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.IOException;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.legacy.StudyV2;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import java.io.File;

/**
 * Deserialization of an JSON file to a study. The study's JSON string can be in different versions of the study to
 * support older JATOS' versions.
 *
 * @author Kristian Lange
 */
public class StudyDeserializer {

    private final IOUtils ioUtils;

    @Inject
    StudyDeserializer(IOUtils ioUtils) {
        this.ioUtils = ioUtils;
    }

    /**
     * Accepts a file with the content of a JSON String and turns the data object within this JSON String into an object
     * of type Study. It can handle different versions of the study model. The version is determined by the version
     * field in the JSON string.
     */
    public Study deserialize(File file) {
        String jsonStr = ioUtils.readFile(file);

        JsonNode node = JsonUtils.parse(jsonStr);
        int version = node.findValue(JsonUtils.VERSION).asInt();
        if (version > Study.SERIAL_VERSION) {
            throw new IOException(MessagesStrings.TOO_NEW_STUDY_VERSION);
        }
        Study study;
        switch (version) {
            case 0:
            case 2:
                // Version 2
                study = JsonUtils.parse(node.findValue(JsonUtils.DATA), StudyV2.class).toStudy();
                break;
            case 3:
                // Current version
                study = JsonUtils.parse(node.findValue(JsonUtils.DATA), Study.class);
                break;
            default:
                throw new IOException(MessagesStrings.UNSUPPORTED_STUDY_VERSION);
        }
        return study;
    }

}
