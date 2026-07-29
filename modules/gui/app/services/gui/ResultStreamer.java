package services.gui;

import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.util.ByteString;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.common.JatosException;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.*;
import models.common.workers.Worker;
import models.common.workers.WorkerType;
import play.Logger;
import play.libs.Json;
import play.mvc.Http;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 */
@Singleton
public class ResultStreamer {

    private static final Logger.ALogger LOGGER = Logger.of(ResultStreamer.class);

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyDao studyDao;
    private final DomainJsonMapper domainJsonMapper;
    private final AuthorizationService authorizationService;
    private final StudyLogger studyLogger;
    private final ComponentResultIdsExtractor componentResultIdsExtractor;

    @Inject
    ResultStreamer(ComponentResultDao componentResultDao,
                   StudyResultDao studyResultDao,
                   StudyDao studyDao,
                   DomainJsonMapper domainJsonMapper,
                   AuthorizationService authorizationService,
                   StudyLogger studyLogger,
                   ComponentResultIdsExtractor componentResultIdsExtractor) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyDao = studyDao;
        this.domainJsonMapper = domainJsonMapper;
        this.authorizationService = authorizationService;
        this.studyLogger = studyLogger;
        this.componentResultIdsExtractor = componentResultIdsExtractor;
    }

    /**
     * Uses a Pekko Source to stream StudyResults (including their result data) that belong to the given Study from the
     * database.
     */
    public Source<ByteString, ?> streamStudyResultsByStudy(Study study) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByStudyPaginated(writer, study);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByStudy: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByStudyPaginated(Writer writer, Study study) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return studyResultDao.countByStudy(study);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<StudyResult> resultList = studyResultDao.findAllByStudy(study, first, maxDbQuerySize);
                writeStudyResults(writer, isLastPage, resultList);
            });
        }
    }

    /**
     * Uses a Pekko Source to stream StudyResults (including their result data) that belong to the given Batch and
     * worker type from the database. If the worker type is empty, it returns all results of this Batch.
     */
    public Source<ByteString, ?> streamStudyResultsByBatch(WorkerType workerType, Batch batch) {
        if (workerType == WorkerType.NONE) {
            return StreamConverters.asOutputStream()
                    .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                    .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                        try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                            writer.write("[");
                            fetchStudyResultsByBatchPaginated(writer, batch);
                            writer.write("]");
                            writer.flush();
                        } catch (Exception e) {
                            LOGGER.error(".streamStudyResultsByBatch: ", e);
                        }
                    }));
        } else {
            return StreamConverters.asOutputStream()
                    .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                    .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                        try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                            writer.write("[");
                            fetchStudyResultsByBatchAndWorkerTypePaginated(writer, batch, workerType);
                            writer.write("]");
                            writer.flush();
                        } catch (Exception e) {
                            LOGGER.error(".streamStudyResultsByBatch: ", e);
                        }
                    }));
        }
    }

    private void fetchStudyResultsByBatchPaginated(Writer writer, Batch batch) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return studyResultDao.countByBatchExcludingWorkerType(batch, WorkerType.JATOS);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<StudyResult> resultList = studyResultDao.findAllByBatch(batch, WorkerType.JATOS, first, maxDbQuerySize);
                writeStudyResults(writer, isLastPage, resultList);
            });
        }
    }

    private void fetchStudyResultsByBatchAndWorkerTypePaginated(Writer writer, Batch batch, WorkerType workerType) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return studyResultDao.countByBatchAndWorkerType(batch, workerType);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<StudyResult> resultList = studyResultDao.findAllByBatchAndWorkerType(batch, workerType, first, maxDbQuerySize);
                writeStudyResults(writer, isLastPage, resultList);
            });
        }
    }

    /**
     * Uses a Pekko Source to stream StudyResults (including their result data) that belong to the given GroupResult
     * from the database.
     */
    public Source<ByteString, ?> streamStudyResultsByGroup(GroupResult groupResult) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByGroupPaginated(writer, groupResult);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByGroup: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByGroupPaginated(Writer writer, GroupResult group) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return studyResultDao.countByGroup(group);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<StudyResult> resultList = studyResultDao.findAllByGroup(group, first, maxDbQuerySize);
                writeStudyResults(writer, isLastPage, resultList);
            });
        }
    }

    /**
     * Uses a Pekko Source to stream StudyResults (including their result data) that belong to the given Worker from the
     * database.
     */
    public Source<ByteString, ?> streamStudyResultsByWorker(Worker worker) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByWorkerPaginated(writer, worker, signedinUser);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByWorker: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByWorkerPaginated(Writer writer, Worker worker, User user) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return studyResultDao.countByWorker(worker, user);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<StudyResult> resultList = studyResultDao.findAllByWorker(worker, user, first, maxDbQuerySize);
                writeStudyResults(writer, isLastPage, resultList);
            });
        }
    }

    /**
     * Uses a Pekko Source to stream ComponentResults (including their result data) that belong to the given Component
     * from the database.
     */
    public Source<ByteString, ?> streamComponentResults(Component component) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchComponentResultsPaginated(writer, component);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResults: ", e);
                    }
                }));
    }

    private void fetchComponentResultsPaginated(Writer writer, Component component) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = studyResultDao.withReadOnlyTransaction(_ -> {
            return componentResultDao.countByComponent(component);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            studyResultDao.withReadOnlyTransaction(_ -> {
                List<ComponentResult> resultList = componentResultDao.findAllByComponent(component, first, maxDbQuerySize);
                writeComponentResults(writer, isLastPage, resultList);
            });
        }
    }

    public Source<ByteString, ?> streamComponentResultData(Http.Request request) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<Long> componentResultIdList = componentResultIdsExtractor.extract(request.body().asJson());
        componentResultIdList.addAll(componentResultIdsExtractor.extract(request.queryString()));
        Collections.sort(componentResultIdList);
        List<Long> studyResultIdList = studyResultDao.findIdsByComponentResultIds(componentResultIdList);
        List<Study> studyList = studyDao.findByStudyResultIds(studyResultIdList);
        for (Study study : studyList) {
            authorizationService.canUserAccessStudy(study, signedinUser);
        }
        studyList.forEach(s -> studyLogger.log(s, signedinUser, "Exported result data"));
        return streamComponentResultData(componentResultIdList);
    }

    /**
     * Returns a Pekko Source that streams all data of the given component results specified by their IDs.
     */
    private Source<ByteString, ?> streamComponentResultData(List<Long> componentResultIdList) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writeComponentResultDataByIds(writer, componentResultIdList, signedinUser);
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResult: ", e);
                    }
                }));
    }

    /**
     * Fetches the ComponentResults that correspond to the IDs, checks them and writes their result data into the given
     * Writer. Fetches them one by one to reduce memory usage.
     */
    void writeComponentResultDataByIds(Writer writer, List<Long> componentResultIdList, User user) {
        Set<Study> studies = new HashSet<>();
        for (Long componentResultId : componentResultIdList) {
            studyResultDao.withReadOnlyTransaction(_ -> {
                ComponentResult componentResult = componentResultDao.findById(componentResultId);
                if (componentResult != null) {
                    authorizationService.canUserAccessComponentResult(componentResult, user, false);
                    studies.add(componentResult.getStudyResult().getStudy());
                    writeComponentResultData(writer, componentResult);
                } else {
                    LOGGER.warn(".fetchComponentResultDataByIds: A component result with ID " + componentResultId + " doesn't exist.");
                }
            });
        }
        studies.forEach(study -> studyLogger.log(study, user, "Exported result data to file"));
    }

    void writeStudyResults(Writer writer, boolean isLastPage, List<StudyResult> resultList) {
        try {
            List<Long> srids = resultList.stream().map(StudyResult::getId).collect(Collectors.toList());
            Map<Long, Integer> componentResultCounts = studyResultDao.countComponentResultsForStudyResultIds(srids);
            for (int i = 0; i < resultList.size(); i++) {
                StudyResult result = resultList.get(i);
                Integer componentResultCount = componentResultCounts.get(result.getId());
                JsonNode resultNode = domainJsonMapper.studyResultAsJsonNode(result, componentResultCount);
                writer.write(resultNode.toString());
                boolean isLastResult = (i + 1) >= resultList.size();
                if (!isLastPage || !isLastResult) {
                    writer.write(",\n");
                }
            }
        } catch (IOException e) {
            throw new JatosException(e);
        }
    }

    void writeComponentResults(Writer writer, boolean isLastPage, List<ComponentResult> resultList) {
        try {
            for (int j = 0; j < resultList.size(); j++) {
                ComponentResult result = resultList.get(j);
                JsonNode resultNode = domainJsonMapper.componentResultAsJsonNode(result);
                writer.write(resultNode.toString());
                boolean isLastResult = (j + 1) >= resultList.size();
                if (!isLastPage || !isLastResult) {
                    writer.write(",\n");
                }
            }
        } catch (IOException e) {
            throw new JatosException(e);
        }
    }

    void writeComponentResultData(Writer writer, ComponentResult componentResult) {
        try {
            String resultData = componentResultDao.getData(componentResult.getId());
            if (resultData == null) return;
            writer.write(resultData + System.lineSeparator());
        } catch (IOException e) {
            throw new JatosException(e);
        }
    }

    public enum ResultType {
        COMBINED, // Metadata, data, and files
        DATA_ONLY,
        FILES_ONLY,
        METADATA_ONLY
    }

    public Source<ByteString, ?> streamResults(Http.Request request, ResultType resultType) {
        return streamResults(request, resultType, Collections.emptyMap());
    }

    public Source<ByteString, ?> streamResults(Http.Request request, ResultType resultType,
                                               Map<String, Object> wrapObject) {
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        Collections.sort(crids);
        return streamResults(crids, resultType, wrapObject);
    }

    /**
     * Returns a Source that streams ComponentResults. The content of what is written into the Source can be specified
     * by a ResultsType.
     */
    private Source<ByteString, ?> streamResults(List<Long> componentResultIds, ResultType resultsType,
                                                Map<String, Object> wrapObject) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (ZipOutputStream zipOut = new ZipOutputStream(outputStream, UTF_8)) {
                        studyResultDao.withReadOnlyTransaction(_ -> {
                            writeResults(componentResultIds, signedinUser, zipOut, resultsType, wrapObject);
                        });
                        zipOut.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamResults: " + e.getMessage());
                    }
                }));
    }

    /**
     * Returns a file with metadata
     */
    public Path writeResultMetadata(Http.Request request, Map<String, Object> wrapObject) {
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        Collections.sort(crids);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return studyResultDao.withReadOnlyTransaction(_ -> {
            return writeResults(crids, signedinUser, null, ResultType.METADATA_ONLY, wrapObject);
        });
    }

    /**
     * Allows streaming of ComponentResults into a ZipOutputStream and on the same side can return a File containing all
     * metadata in JSON format. The content of what is written into the ZipOutputStream can be specified by a
     * ResultsType.
     */
    Path writeResults(List<Long> componentResultIds, User signedinUser, ZipOutputStream zipOut,
                      ResultType resultsType, Map<String, Object> wrapObject) {
        try {
            List<Long> studyResultIds = studyResultDao.findIdsByComponentResultIds(componentResultIds);

            Path metadataFile = null;
            JsonGenerator jGenerator;
            if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                metadataFile = Files.createTempFile("metadata", "json");
                jGenerator = Json.mapper().getFactory().createGenerator(metadataFile.toFile(), JsonEncoding.UTF8);
                jGenerator.writeStartObject();
                if (!wrapObject.isEmpty()) {
                    for (Map.Entry<String, Object> e : wrapObject.entrySet()) {
                        jGenerator.writeObjectField(e.getKey(), e.getValue());
                    }
                }
                jGenerator.writeArrayFieldStart("data");
            } else {
                jGenerator = null;
            }

            List<Long> studyIds = studyDao.findIdsByStudyResultIds(studyResultIds);
            for (Long studyId : studyIds) {
                Study study = studyDao.findById(studyId);
                authorizationService.canUserAccessStudy(study, signedinUser);

                if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                    jGenerator.writeStartObject();
                    jGenerator.writeNumberField("studyId", study.getId());
                    jGenerator.writeStringField("studyUuid", study.getUuid());
                    jGenerator.writeStringField("studyTitle", study.getTitle());
                    jGenerator.writeArrayFieldStart("studyResults");
                }

                List<Long> sridsByStudy = studyResultDao.findIdsFromListThatBelongToStudy(studyResultIds, study.getId());
                writeStudyResultsToZip(componentResultIds, sridsByStudy, zipOut, jGenerator, resultsType);

                if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                    jGenerator.writeEndArray();
                    jGenerator.writeEndObject();
                }
                if (resultsType == ResultType.COMBINED || resultsType == ResultType.DATA_ONLY || resultsType == ResultType.FILES_ONLY) {
                    studyLogger.log(study, signedinUser, "Exported results (files and/or data)");
                }
            }

            if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                jGenerator.writeEndArray();
                if (!wrapObject.isEmpty()) jGenerator.writeEndObject();
                jGenerator.close();
            }

            if (resultsType == ResultType.COMBINED) {
                ZipUtil.addFileToZip(zipOut, Path.of(""), Path.of("metadata.json"), metadataFile);
                Files.delete(metadataFile);
            }
            return resultsType == ResultType.METADATA_ONLY ? metadataFile : null;
        } catch (IOException e) {
            throw new JatosException(e);
        }
    }

    private void writeStudyResultsToZip(List<Long> crids, List<Long> srids, ZipOutputStream zipOut,
                                        JsonGenerator jGenerator, ResultType resultsType) {
        try {
            int maxDbQuerySize = Common.getMaxResultsDbQuerySize();

            for (int i = 0; i < srids.size(); i += maxDbQuerySize) {
                List<StudyResult> studyResultList = studyResultDao.findByIds(srids, i, maxDbQuerySize);

                for (StudyResult studyResult : studyResultList) {
                    // Filter: Keep only the crids that are in the original request's crids (StudyResult can have more)
                    List<Long> someCrids = componentResultDao.findIdsByStudyResultId(studyResult.getId())
                            .stream().filter(crids::contains).collect(Collectors.toList());
                    ArrayNode componentResultArrayNode = writeComponentResultsToZip(studyResult.getId(), someCrids, zipOut, resultsType);

                    if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                        ObjectNode studyResultNode = domainJsonMapper.studyResultMetadata(studyResult);
                        studyResultNode.set("componentResults", componentResultArrayNode);
                        jGenerator.writeTree(studyResultNode);
                    }
                }
            }
        } catch (IOException e) {
            throw new JatosException(e);
        }
    }

    ArrayNode writeComponentResultsToZip(Long studyResultId, List<Long> componentResultList, ZipOutputStream zipOut,
                                         ResultType resultsType) {
        ArrayNode componentResultArrayNode = Json.mapper().createArrayNode();
        for (Long componentResultId : componentResultList) {
            // We have to do it one by one to save memory in case of large result data
            studyResultDao.withReadOnlyTransaction(_ -> {
                switch (resultsType) {
                    case METADATA_ONLY: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        componentResultArrayNode.add(domainJsonMapper.componentResultMetadata(componentResult));
                        break;
                    }
                    case FILES_ONLY: {
                        addFilesToZip(zipOut, studyResultId, componentResultId);
                        break;
                    }
                    case DATA_ONLY: {
                        String data = componentResultDao.getData(componentResultId);
                        String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                        ZipUtil.addDataToZip(zipOut, data, path);
                        break;
                    }
                    case COMBINED: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        componentResultArrayNode.add(domainJsonMapper.componentResultMetadata(componentResult));
                        addFilesToZip(zipOut, studyResultId, componentResultId);
                        String data = componentResultDao.getData(componentResultId);
                        String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                        ZipUtil.addDataToZip(zipOut, data, path);
                        break;
                    }
                }
            });
        }
        return componentResultArrayNode;
    }

    private void addFilesToZip(ZipOutputStream zipOut, Long studyResultId, Long componentResultId) {
        Path pathInFileSystem = IOUtils.getResultUploadsDir(studyResultId, componentResultId);
        if (Files.exists(pathInFileSystem)) {
            Path pathInZip = Path.of(IOUtils.getResultsPathForZip(studyResultId, componentResultId), "files");
            ZipUtil.addToZip(zipOut, pathInZip, pathInFileSystem);
        }
    }

}
