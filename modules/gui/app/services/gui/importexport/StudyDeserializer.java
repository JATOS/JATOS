package services.gui.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.ImportExportException;
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
     * Reads a study from a JSON file, supporting older study format versions.
     */
    public Study deserialize(Path file) {
        String jsonStr = unchecked(() -> Files.readString(file));
        JsonNode node = mapper.jsonAsJsonNode(jsonStr);

        if (node == null || !node.hasNonNull("version") || !node.path("data").isObject())
            throw new ImportExportException("File is not a valid JATOS study.");
        int version = node.path("version").asInt();
        Study study = switch (version) {
            case 0, 1, 2 ->
                    // Version 2
                    throw new ImportExportException("Support for this version of the study model has been removed.");
            case 3 ->
                    // Current version
                    mapper.jsonNodeAsObj(node.findValue("data"), Study.class);
            default ->
                    throw new ImportExportException("This study is from an unsupported version of JATOS.");
        };
        return study;
    }

}
