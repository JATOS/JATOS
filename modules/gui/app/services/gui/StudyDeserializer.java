package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import json.common.DefaultJson;
import models.common.Study;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;

import static exceptions.common.JatosException.unchecked;

/**
 * Deserialization of an JSON file to a study. The study's JSON string can be in different versions of the study to
 * support older JATOS' versions.
 */
@Singleton
public class StudyDeserializer {

    private final DefaultJson mapper;

    @Inject
    StudyDeserializer(DefaultJson mapper) {
        this.mapper = mapper;
    }

    /**
     * Accepts a file with the content of a JSON String and turns the data object within this JSON String into an object
     * of type Study. It can handle different versions of the study model. The version is determined by the version
     * field in the JSON string.
     */
    public Study deserialize(Path file) {
        String jsonStr = unchecked(() -> Files.readString(file));
        JsonNode node = mapper.jsonAsJsonNode(jsonStr);

        int version = node.findValue("version").asInt();
        Study study;
        switch (version) {
            case 0:
            case 1:
            case 2:
                // Version 2
                throw new JatosException("Support for this version of the study model has been removed.", ErrorCode.IMPORT_EXPORT_ERROR);
            case 3:
                // Current version
                study = mapper.jsonNodeAsObj(node.findValue("data"), Study.class);
                break;
            default:
                throw new JatosException("This study is from an unsupported version of JATOS.", ErrorCode.IMPORT_EXPORT_ERROR);
        }
        return study;
    }

}
