package services.gui;

import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;
import auth.gui.AuthService;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import play.mvc.Http;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultStreamer {

    private static final Logger.ALogger LOGGER = Logger.of(ResultStreamer.class);

    private final AuthService authenticationService;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyDao studyDao;
    private final JsonUtils jsonUtils;
    private final Checker checker;
    private final StudyLogger studyLogger;
    private final ComponentResultIdsExtractor componentResultIdsExtractor;
    private final JPAApi jpaApi;

    @Inject
    ResultStreamer(AuthService authenticationService, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyDao studyDao, JsonUtils jsonUtils, Checker checker,
            StudyLogger studyLogger, ComponentResultIdsExtractor componentResultIdsExtractor, JPAApi jpaApi) {
        this.authenticationService = authenticationService;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyDao = studyDao;
        this.jsonUtils = jsonUtils;
        this.checker = checker;
        this.studyLogger = studyLogger;
        this.componentResultIdsExtractor = componentResultIdsExtractor;
        this.jpaApi = jpaApi;
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Study
     * from the database.
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
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByStudy(study);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByStudy(study, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Batch and worker
     * type from the database. If the worker type is empty it returns all results of this Batch.
     */
    public Source<ByteString, ?> streamStudyResultsByBatch(String workerType, Batch batch) {
        if (Strings.isNullOrEmpty(workerType)) {
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
                            // If worker type is MT then add MTSandbox on top
                            if (MTWorker.WORKER_TYPE.equals(workerType)) {
                                fetchStudyResultsByBatchAndWorkerTypePaginated(writer, batch, MTSandboxWorker.WORKER_TYPE);
                            }
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
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatch(batch, JatosWorker.WORKER_TYPE);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByBatch(batch, JatosWorker.WORKER_TYPE, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    private void fetchStudyResultsByBatchAndWorkerTypePaginated(Writer writer, Batch batch, String workerType) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatchAndWorkerType(batch, workerType);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao
                        .findAllByBatchAndWorkerType(batch, workerType, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given GroupResult
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
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByGroup(group);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByGroup(group, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Worker
     * from the database.
     */
    public Source<ByteString, ?> streamStudyResultsByWorker(User loggedInUser, Worker worker) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByWorkerPaginated(writer, worker, loggedInUser);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByWorker: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByWorkerPaginated(Writer writer, Worker worker, User user) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByWorker(worker, user);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByWorker(worker, user, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream ComponentResults (including their result data) that belong to the given Component
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
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return componentResultDao.countByComponent(component);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                Errors.rethrow().run(() -> {
                    List<ComponentResult> resultList = componentResultDao.findAllByComponent(component, first,
                            maxDbQuerySize);
                    writeComponentResult(writer, isLastPage, resultList);
                });
            });
        }
    }

    public Source<ByteString, ?> streamComponentResultData(Http.Request request)
            throws BadRequestException, ForbiddenException, NotFoundException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = componentResultIdsExtractor.extract(request.body().asJson());
        componentResultIdList.addAll(componentResultIdsExtractor.extract(request.queryString()));
        List<Long> studyResultIdList = studyResultDao.findIdsByComponentResultIds(componentResultIdList);
        List<Study> studyList = studyDao.findByStudyResultIds(studyResultIdList);
        for (Study study : studyList) {
            checker.checkStandardForStudy(study, study.getId(), loggedInUser);
        }
        studyList.forEach(s -> studyLogger.log(s, loggedInUser, "Exported result data"));
        return streamComponentResultData(loggedInUser, componentResultIdList);
    }

    /**
     * Returns an Akka Source that streams all data of the given component results specified by their IDs.
     */
    private Source<ByteString, ?> streamComponentResultData(User loggedInUser, List<Long> componentResultIdList) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        fetchComponentResultDataByIds(writer, componentResultIdList, loggedInUser);
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResult: ", e);
                    }
                }));
    }

    /**
     * Fetches the ComponentResults that correspond to the IDs, checks them and writes their result data into the given
     * Writer. Fetches them one by one to reduce memory usages.
     */
    private void fetchComponentResultDataByIds(Writer writer, List<Long> componentResultIdList, User user) {
        Set<Study> studies = new HashSet<>();
        for (Long componentResultId : componentResultIdList) {
            jpaApi.withTransaction(entityManager -> {
                ComponentResult componentResult = componentResultDao.findById(componentResultId);
                if (componentResult != null) {
                    Errors.rethrow().run(() -> checker.checkComponentResult(componentResult, user, false));
                    studies.add(componentResult.getStudyResult().getStudy());
                    Errors.rethrow().run(() -> writeComponentResultData(writer, componentResult));
                } else {
                    LOGGER.warn("A component result with ID " + componentResultId + " doesn't exist.");
                }
            });
        }
        studies.forEach(study -> studyLogger.log(study, user, "Exported result data to file"));
    }

    private void writeStudyResults(Writer writer, boolean isLastPage, List<StudyResult> resultList) throws IOException {
        List<Long> srids = resultList.stream().map(StudyResult::getId).collect(Collectors.toList());
        Map<Long, Integer> componentResultCounts = studyResultDao.countComponentResultsForStudyResultIds(srids);
        for (int i = 0; i < resultList.size(); i++) {
            StudyResult result = resultList.get(i);
            Integer componentResultCount = componentResultCounts.get(result.getId());
            JsonNode resultNode = jsonUtils.studyResultAsJsonNode(result, componentResultCount);
            writer.write(resultNode.toString());
            boolean isLastResult = (i + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                writer.write(",\n");
            }
        }
    }

    private void writeComponentResult(Writer writer, boolean isLastPage, List<ComponentResult> resultList)
            throws IOException {
        for (int j = 0; j < resultList.size(); j++) {
            ComponentResult result = resultList.get(j);
            JsonNode resultNode = jsonUtils.componentResultAsJsonNode(result);
            writer.write(resultNode.toString());
            boolean isLastResult = (j + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                writer.write(",\n");
            }
        }
    }

    private void writeComponentResultData(Writer writer, ComponentResult componentResult) throws IOException {
        String resultData = componentResultDao.getData(componentResult.getId());
        if (resultData == null) return;
        writer.write(resultData + System.lineSeparator());
    }

    public enum ResultType {
        COMBINED, // Metadata, data, and files
        DATA_ONLY,
        FILES_ONLY,
        METADATA_ONLY
    }

    public Source<ByteString, ?> streamResults(Http.Request request, ResultType resultType) throws BadRequestException {
        return streamResults(request, resultType, Collections.emptyMap());
    }

    public Source<ByteString, ?> streamResults(Http.Request request, ResultType resultType,
            Map<String, Object> wrapObject) throws BadRequestException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        return streamResults(crids, loggedInUser, resultType, wrapObject);
    }

    /**
     * Returns a Source that streams ComponentResults. The content of what is written into the Source can be
     * specified by a ResultsType.
     */
    private Source<ByteString, ?> streamResults(List<Long> componentResultIds, User loggedInUser, ResultType resultsType,
            Map<String, Object> wrapObject) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (ZipOutputStream zipOut = new ZipOutputStream(outputStream, UTF_8)) {
                        jpaApi.withTransaction(entityManager -> {
                            Errors.rethrow().run(() -> writeResults(componentResultIds, loggedInUser, zipOut, resultsType, wrapObject));
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
    public File writeResultMetadata(Http.Request request, Map<String, Object> wrapObject)
            throws ForbiddenException, NotFoundException, IOException, BadRequestException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        return writeResults(crids, loggedInUser, null, ResultType.METADATA_ONLY, wrapObject);
    }

    /**
     * Allows streaming of ComponentResults into a ZipOutputStream and on the same side can return a File containing all
     * metadata in JSON format. The content of what is written into the ZipOutputStream can be specified by a ResultsType.
     */
    private File writeResults(List<Long> componentResultIds, User loggedInUser, ZipOutputStream zipOut,
            ResultType resultsType, Map<String, Object> wrapObject) throws IOException, NotFoundException, ForbiddenException {
        List<Long> studyResultIds = studyResultDao.findIdsByComponentResultIds(componentResultIds);

        Path metadataFile = null;
        JsonGenerator jGenerator;
        if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
            metadataFile = Files.createTempFile("metadata", "json");
            jGenerator = Json.mapper().getFactory().createGenerator(metadataFile.toFile(), JsonEncoding.UTF8);
            if (wrapObject.isEmpty()) {
                jGenerator.writeStartArray();
            } else {
                jGenerator.writeStartObject();
                for (Map.Entry<String, Object> e : wrapObject.entrySet()) {
                    jGenerator.writeObjectField(e.getKey(), e.getValue());
                }
                jGenerator.writeArrayFieldStart("data");
            }
        } else {
            jGenerator = null;
        }

        List<Long> studyIds = studyDao.findIdsByStudyResultIds(studyResultIds);
        for (Long studyId : studyIds) {
            Study study = studyDao.findById(studyId);
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                jGenerator.writeStartObject();
                jGenerator.writeNumberField("studyId", study.getId());
                jGenerator.writeStringField("studyUuid", study.getUuid());
                jGenerator.writeStringField("studyTitle", study.getTitle());
                jGenerator.writeArrayFieldStart("studyResults");
            }

            List<Long> sridsByStudy = studyResultDao.findIdsFromListThatBelongToStudy(studyResultIds, study.getId());
            writeStudyResults(componentResultIds, sridsByStudy, zipOut, jGenerator, resultsType);

            if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                jGenerator.writeEndArray();
                jGenerator.writeEndObject();
            }
            if (resultsType == ResultType.COMBINED || resultsType == ResultType.DATA_ONLY || resultsType == ResultType.FILES_ONLY) {
                studyLogger.log(study, loggedInUser, "Exported results (files and/or data)");
            }
        }

        if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
            jGenerator.writeEndArray();
            if (!wrapObject.isEmpty()) jGenerator.writeEndObject();
            jGenerator.close();
        }

        if (resultsType == ResultType.COMBINED) {
            ZipUtil.addFileToZip(zipOut, Paths.get("/"), Paths.get("metadata.json"), metadataFile);
            Files.delete(metadataFile);
        }
        return resultsType == ResultType.METADATA_ONLY ? metadataFile.toFile(): null;
    }

    private void writeStudyResults(List<Long> crids, List<Long> srids, ZipOutputStream zipOut,
            JsonGenerator jGenerator, ResultType resultsType) throws IOException {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();

        for (int i = 0; i < srids.size(); i += maxDbQuerySize) {
            List<StudyResult> studyResultList = studyResultDao.findByIds(srids, i, maxDbQuerySize);

            for (StudyResult studyResult : studyResultList) {
                // Filter: Keep only the crids that are in the original request's crids (StudyResult can have more)
                List<Long> someCrids = componentResultDao.findIdsByStudyResultId(studyResult.getId())
                        .stream().filter(crids::contains).collect(Collectors.toList());
                ArrayNode componentResultArrayNode = writeComponentResults(studyResult.getId(), someCrids, zipOut, resultsType);

                if (resultsType == ResultType.METADATA_ONLY || resultsType == ResultType.COMBINED) {
                    ObjectNode studyResultNode = jsonUtils.studyResultMetadata(studyResult);
                    studyResultNode.set("componentResults", componentResultArrayNode);
                    jGenerator.writeTree(studyResultNode);
                }
            }
        }
    }

    private ArrayNode writeComponentResults(Long studyResultId, List<Long> componentResultList, ZipOutputStream zipOut,
            ResultType resultsType) {
        ArrayNode componentResultArrayNode = Json.mapper().createArrayNode();
        for (Long componentResultId : componentResultList) {
            // We have to do it one by one to save memory in case of large result data
            jpaApi.withTransaction(entityManager -> {
                switch (resultsType) {
                    case METADATA_ONLY: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        componentResultArrayNode.add(jsonUtils.componentResultMetadata(componentResult));
                        break;
                    }
                    case FILES_ONLY: {
                        Errors.rethrow().run(() -> addFilesToZip(zipOut, studyResultId, componentResultId));
                        break;
                    }
                    case DATA_ONLY: {
                        Errors.rethrow().run(() -> {
                            String data = componentResultDao.getData(componentResultId);
                            String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                            ZipUtil.addDataToZip(zipOut, data, path);
                        });
                        break;
                    }
                    case COMBINED: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        componentResultArrayNode.add(jsonUtils.componentResultMetadata(componentResult));
                        Errors.rethrow().run(() -> addFilesToZip(zipOut, studyResultId, componentResultId));
                        Errors.rethrow().run(() -> {
                            String data = componentResultDao.getData(componentResultId);
                            String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                            ZipUtil.addDataToZip(zipOut, data, path);
                        });
                        break;
                    }
                }
            });
        }
        return componentResultArrayNode;
    }

    private void addFilesToZip(ZipOutputStream zipOut, Long studyResultId, Long componentResultId) throws IOException {
        Path pathInFileSystem = Paths.get(IOUtils.getResultUploadsDir(studyResultId, componentResultId));
        if (Files.exists(pathInFileSystem)) {
            Path pathInZip = Paths.get("/", IOUtils.getResultsPathForZip(studyResultId, componentResultId), "files");
            ZipUtil.addToZip(zipOut, pathInZip, pathInFileSystem);
        }
    }

}
