package services.gui;

import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;
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
import java.util.List;
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

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyDao studyDao;
    private final JsonUtils jsonUtils;
    private final Checker checker;
    private final StudyLogger studyLogger;
    private final JPAApi jpaApi;

    @Inject
    ResultStreamer(ComponentResultDao componentResultDao, StudyResultDao studyResultDao, StudyDao studyDao,
            JsonUtils jsonUtils, Checker checker, StudyLogger studyLogger, JPAApi jpaApi) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyDao = studyDao;
        this.jsonUtils = jsonUtils;
        this.checker = checker;
        this.studyLogger = studyLogger;
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
                        LOGGER.error(".streamStudyResultsByStudy: " + e.getMessage());
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
                            LOGGER.error(".streamStudyResultsByBatch: " + e.getMessage());
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
                            LOGGER.error(".streamStudyResultsByBatch: " + e.getMessage());
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
                        LOGGER.error(".streamStudyResultsByGroup: " + e.getMessage());
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
                        LOGGER.error(".streamStudyResultsByWorker: " + e.getMessage());
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
                        LOGGER.error(".streamComponentResults: " + e.getMessage());
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

    /**
     * Returns an Akka Source that streams all data of the given component results specified by their IDs.
     */
    public Source<ByteString, ?> streamComponentResult(List<Long> componentResultIdList) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        fetchComponentResultByIds(writer, componentResultIdList);
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResult: " + e.getMessage());
                    }
                }));
    }

    /**
     * Fetches the ComponentResults that correspond to the IDs, checks them and writes their result data into the given
     * Writer. Fetches them one by one to reduce memory usages.
     */
    private void fetchComponentResultByIds(Writer writer, List<Long> componentResultIdList) {
        for (Long componentResultId : componentResultIdList) {
            jpaApi.withTransaction(entityManager -> {
                Errors.rethrow().run(() -> {
                    ComponentResult cr = componentResultDao.findById(componentResultId);
                    if (cr.getData() != null) writer.write(cr.getData() + System.lineSeparator());
                    else writer.write(System.lineSeparator());
                });
            });
        }
    }

    private void writeStudyResults(Writer writer, boolean isLastPage, List<StudyResult> resultList) throws IOException {
        for (int i = 0; i < resultList.size(); i++) {
            StudyResult result = resultList.get(i);
            int componentResultCount = componentResultDao.countByStudyResult(result);
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

    public enum ResultsType {
        COMBINED, // Metadata, data, and files
        DATA_ONLY,
        FILES_ONLY,
        METADATA_ONLY
    }

    /**
     * Returns a Source that streams ComponentResults. The content of what is written into the Source can be
     * specified by a ResultsType.
     */
    public Source<ByteString, ?> streamResults(List<Long> componentResultIds, User loggedInUser, ResultsType resultsType) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (ZipOutputStream zipOut = new ZipOutputStream(outputStream, UTF_8)) {
                        jpaApi.withTransaction(entityManager -> {
                            Errors.rethrow().run(() -> writeResults(componentResultIds, loggedInUser, zipOut, resultsType));
                        });
                        zipOut.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamResults: " + e.getMessage());
                    }
                }));
    }

    /**
     * Returns a file with metadata of the given ComponentResults
     */
    public File writeResultsMetadata(List<Long> componentResultIds, User loggedInUser)
            throws ForbiddenException, NotFoundException, IOException {
        return writeResults(componentResultIds, loggedInUser, null, ResultsType.METADATA_ONLY);
    }

    /**
     * Allows streaming of ComponentResults into a ZipOutputStream and on the same side can return a File containing all
     * metadata in JSON format. The content of what is written into the ZipOutputStream can be specified by a ResultsType.
     */
    private File writeResults(List<Long> componentResultIds, User loggedInUser, ZipOutputStream zipOut,
            ResultsType resultsType) throws IOException, NotFoundException, ForbiddenException {
        List<Long> studyResultIds = studyResultDao.findIdsByComponentResultIds(componentResultIds);

        Path metadataFile = null;
        JsonGenerator jGenerator = null;
        if (resultsType == ResultsType.METADATA_ONLY || resultsType == ResultsType.COMBINED) {
            metadataFile = Files.createTempFile("metadata", "json");
            jGenerator = Json.mapper().getFactory().createGenerator(metadataFile.toFile(), JsonEncoding.UTF8);
            jGenerator.writeStartArray();
        }

        List<Long> studyIds = studyDao.findIdsByStudyResultIds(studyResultIds);
        for (Long studyId : studyIds) {
            Study study = studyDao.findById(studyId);
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            if (resultsType == ResultsType.METADATA_ONLY || resultsType == ResultsType.COMBINED) {
                jGenerator.writeStartObject();
                jGenerator.writeNumberField("studyId", study.getId());
                jGenerator.writeStringField("studyUuid", study.getUuid());
                jGenerator.writeStringField("studyTitle", study.getTitle());
                jGenerator.writeArrayFieldStart("studyResults");
            }

            List<Long> sridsByStudy = studyResultDao.findIdsFromListThatBelongToStudy(studyResultIds, study.getId());
            writeStudyResults(componentResultIds, sridsByStudy, zipOut, jGenerator, resultsType);

            if (resultsType == ResultsType.METADATA_ONLY || resultsType == ResultsType.COMBINED) {
                jGenerator.writeEndArray();
                jGenerator.writeEndObject();
            }
            if (resultsType == ResultsType.COMBINED || resultsType == ResultsType.DATA_ONLY || resultsType == ResultsType.FILES_ONLY) {
                studyLogger.log(study, loggedInUser, "Exported results (files and data)");
            }
        }

        if (resultsType == ResultsType.METADATA_ONLY || resultsType == ResultsType.COMBINED) {
            jGenerator.writeEndArray();
            jGenerator.close();
        }

        if (resultsType == ResultsType.COMBINED) {
            ZipUtil.addFileToZip(zipOut, Paths.get("/"), Paths.get("metadata.json"), metadataFile);
            Files.delete(metadataFile);
        }
        return resultsType == ResultsType.METADATA_ONLY ? metadataFile.toFile(): null;
    }

    private void writeStudyResults(List<Long> crids, List<Long> srids, ZipOutputStream zipOut,
            JsonGenerator jGenerator, ResultsType resultsType) throws IOException {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();

        for (int i = 0; i < srids.size(); i += maxDbQuerySize) {
            List<StudyResult> studyResultList = studyResultDao.findByIds(srids, i, maxDbQuerySize);

            for (StudyResult studyResult : studyResultList) {
                // Filter: Keep only the crids that are in the original request's crids (StudyResult can have more)
                List<Long> someCrids = componentResultDao.findIdsByStudyResultId(studyResult.getId())
                        .stream().filter(crids::contains).collect(Collectors.toList());
                ArrayNode componentResultArrayNode = writeComponentResults(someCrids, zipOut, resultsType);

                if (resultsType == ResultsType.METADATA_ONLY || resultsType == ResultsType.COMBINED) {
                    ObjectNode studyResultNode = jsonUtils.studyResultMetadata(studyResult);
                    studyResultNode.set("componentResults", componentResultArrayNode);
                    jGenerator.writeTree(studyResultNode);
                }
            }
        }
    }

    private ArrayNode writeComponentResults(List<Long> componentResultList, ZipOutputStream zipOut,
            ResultsType resultsType) {
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
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        Long studyResultId = componentResult.getStudyResult().getId();
                        Errors.rethrow().run(() -> addFilesToZip(zipOut, studyResultId, componentResultId));
                        break;
                    }
                    case DATA_ONLY: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        Long studyResultId = componentResult.getStudyResult().getId();
                        Errors.rethrow().run(() -> {
                            String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                            ZipUtil.addDataToZip(zipOut, componentResult.getData(), path);
                        });
                        break;
                    }
                    case COMBINED: {
                        ComponentResult componentResult = componentResultDao.findById(componentResultId);
                        Long studyResultId = componentResult.getStudyResult().getId();
                        componentResultArrayNode.add(jsonUtils.componentResultMetadata(componentResult));
                        Errors.rethrow().run(() -> addFilesToZip(zipOut, studyResultId, componentResultId));
                        Errors.rethrow().run(() -> {
                            String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId) + "/data.txt";
                            ZipUtil.addDataToZip(zipOut, componentResult.getData(), path);
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
