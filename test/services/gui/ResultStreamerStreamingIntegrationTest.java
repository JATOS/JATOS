package services.gui;

import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import general.GuiceModule;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.*;
import org.junit.*;
import org.mockito.ArgumentMatchers;
import play.Application;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.test.Helpers;
import services.gui.ResultStreamer.ResultType;
import testutils.JPAMocker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for ResultStreamer's actual Akka Source output.
 */
public class ResultStreamerStreamingIntegrationTest {

    private static Path resultUploadsRoot;

    private Application app;
    private Materializer materializer;

    private ComponentResultDao componentResultDao;
    private StudyResultDao studyResultDao;
    private StudyDao studyDao;
    private DomainJsonMapper domainJsonMapper;
    private AuthorizationService authorizationService;
    private StudyLogger studyLogger;
    private ComponentResultIdsExtractor componentResultIdsExtractor;

    private ResultStreamer resultStreamer;

    private User user;

    @BeforeClass
    public static void initCommonStatics() throws Exception {
        Field maxResultsDbQuerySize = Common.class.getDeclaredField("maxResultsDbQuerySize");
        maxResultsDbQuerySize.setAccessible(true);
        maxResultsDbQuerySize.setInt(null, 10);

        resultUploadsRoot = Files.createTempDirectory("jatos-result-uploads-test-");

        Field resultUploadsPath = Common.class.getDeclaredField("resultUploadsPath");
        resultUploadsPath.setAccessible(true);
        resultUploadsPath.set(null, resultUploadsRoot.toString());
    }

    @AfterClass
    public static void tearDownCommonStatics() throws Exception {
        if (resultUploadsRoot != null && Files.exists(resultUploadsRoot)) {
            deleteRecursively(resultUploadsRoot);
        }
    }

    @Before
    public void setUp() {
        app = new GuiceApplicationBuilder()
                .disable(GuiceModule.class)
                .build();
        Helpers.start(app);

        Injector injector = app.injector();
        materializer = injector.instanceOf(Materializer.class);

        componentResultDao = mock(ComponentResultDao.class);
        studyResultDao = mock(StudyResultDao.class);
        studyDao = mock(StudyDao.class);
        domainJsonMapper = mock(DomainJsonMapper.class);
        authorizationService = mock(AuthorizationService.class);
        studyLogger = mock(StudyLogger.class);
        componentResultIdsExtractor = mock(ComponentResultIdsExtractor.class);

        resultStreamer = new ResultStreamer(
                componentResultDao,
                studyResultDao,
                studyDao,
                domainJsonMapper,
                authorizationService,
                studyLogger,
                componentResultIdsExtractor);

        user = new User();
        user.setUsername("alice");

        JPAMocker.mockDaoTransactions(studyResultDao, componentResultDao, studyDao);
    }

    @After
    public void tearDown() {
        Context.clear();
        if (app != null) {
            Helpers.stop(app);
        }
    }

    @Test
    public void streamComponentResultData_streamsActualResultData() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);

        StudyResult studyResult = new StudyResult();
        studyResult.setId(2L);
        studyResult.setStudy(study);

        Component component = new Component();
        component.setId(3L);
        component.setStudy(study);

        ComponentResult componentResult1 = new ComponentResult();
        componentResult1.setId(10L);
        componentResult1.setStudyResult(studyResult);
        componentResult1.setComponent(component);

        ComponentResult componentResult2 = new ComponentResult();
        componentResult2.setId(20L);
        componentResult2.setStudyResult(studyResult);
        componentResult2.setComponent(component);

        Http.Request request = fakeJsonRequest(Json.newObject());
        setCurrentContextWithSignedinUser(request, user);

        when(componentResultIdsExtractor.extract(any(JsonNode.class))).thenReturn(Arrays.asList(20L, 10L));
        when(componentResultIdsExtractor.extract(ArgumentMatchers.<Map<String, String[]>>any()))
                .thenReturn(Collections.emptyList());

        when(studyResultDao.findIdsByComponentResultIds(Arrays.asList(10L, 20L)))
                .thenReturn(Collections.singletonList(2L));
        when(studyDao.findByStudyResultIds(Collections.singletonList(2L)))
                .thenReturn(Collections.singletonList(study));

        when(componentResultDao.findById(10L)).thenReturn(componentResult1);
        when(componentResultDao.findById(20L)).thenReturn(componentResult2);
        when(componentResultDao.getData(10L)).thenReturn("data-10");
        when(componentResultDao.getData(20L)).thenReturn("data-20");

        // When
        Source<ByteString, ?> source = resultStreamer.streamComponentResultData(request);
        String streamed = materializeToString(source);

        // Then
        assertThat(streamed).isEqualTo(
                "data-10" + System.lineSeparator() +
                        "data-20" + System.lineSeparator());

        verify(authorizationService).canUserAccessStudy(study, user);
        verify(authorizationService).canUserAccessComponentResult(componentResult1, user, false);
        verify(authorizationService).canUserAccessComponentResult(componentResult2, user, false);
        verify(studyLogger).log(study, user, "Exported result data");
        verify(studyLogger).log(study, user, "Exported result data to file");
    }

    @Test
    public void streamResults_dataOnly_streamsZipWithDataFiles() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);
        study.setUuid("study-uuid");
        study.setTitle("Study title");

        StudyResult studyResult = new StudyResult();
        studyResult.setId(2L);
        studyResult.setStudy(study);

        Http.Request request = fakeJsonRequest(Json.newObject());
        setCurrentContextWithSignedinUser(request, user);

        when(componentResultIdsExtractor.extract(any(JsonNode.class))).thenReturn(Arrays.asList(20L, 10L));
        when(componentResultIdsExtractor.extract(ArgumentMatchers.<Map<String, String[]>>any()))
                .thenReturn(Collections.emptyList());

        when(studyResultDao.findIdsByComponentResultIds(Arrays.asList(10L, 20L)))
                .thenReturn(Collections.singletonList(2L));
        when(studyDao.findIdsByStudyResultIds(Collections.singletonList(2L)))
                .thenReturn(Collections.singletonList(1L));
        when(studyDao.findById(1L)).thenReturn(study);
        when(studyResultDao.findIdsFromListThatBelongToStudy(Collections.singletonList(2L), 1L))
                .thenReturn(Collections.singletonList(2L));
        when(studyResultDao.findByIds(eq(Collections.singletonList(2L)), eq(0), anyInt()))
                .thenReturn(Collections.singletonList(studyResult));
        when(componentResultDao.findIdsByStudyResultId(2L))
                .thenReturn(Arrays.asList(10L, 20L));
        when(componentResultDao.getData(10L)).thenReturn("data-10");
        when(componentResultDao.getData(20L)).thenReturn("data-20");

        // When
        Source<ByteString, ?> source = resultStreamer.streamResults(request, ResultType.DATA_ONLY);
        byte[] zipBytes = materializeToBytes(source);
        Map<String, String> zipEntries = readZipEntries(zipBytes);

        // Then
        assertThat(zipEntries.size()).isEqualTo(2);
        assertThat(zipEntries.get("study_result_2/comp-result_10/data.txt")).isEqualTo("data-10");
        assertThat(zipEntries.get("study_result_2/comp-result_20/data.txt")).isEqualTo("data-20");

        verify(authorizationService).canUserAccessStudy(study, user);
        verify(studyLogger).log(study, user, "Exported results (files and/or data)");
    }

    @Test
    public void streamResults_combined_streamsZipWithDataFilesAndMetadataJson() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);
        study.setUuid("study-uuid");
        study.setTitle("Study title");

        StudyResult studyResult = new StudyResult();
        studyResult.setId(2L);
        studyResult.setStudy(study);

        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(10L);
        componentResult.setStudyResult(studyResult);

        Path uploadDir = resultUploadsRoot
                .resolve("study-result_2")
                .resolve("comp-result_10");
        Files.createDirectories(uploadDir);
        Files.writeString(uploadDir.resolve("upload.txt"), "uploaded-file-content", StandardCharsets.UTF_8);

        Http.Request request = fakeJsonRequest(Json.newObject());
        setCurrentContextWithSignedinUser(request, user);

        when(componentResultIdsExtractor.extract(any(JsonNode.class))).thenReturn(Collections.singletonList(10L));
        when(componentResultIdsExtractor.extract(ArgumentMatchers.<Map<String, String[]>>any()))
                .thenReturn(Collections.emptyList());

        when(studyResultDao.findIdsByComponentResultIds(Collections.singletonList(10L)))
                .thenReturn(Collections.singletonList(2L));
        when(studyDao.findIdsByStudyResultIds(Collections.singletonList(2L)))
                .thenReturn(Collections.singletonList(1L));
        when(studyDao.findById(1L)).thenReturn(study);
        when(studyResultDao.findIdsFromListThatBelongToStudy(Collections.singletonList(2L), 1L))
                .thenReturn(Collections.singletonList(2L));
        when(studyResultDao.findByIds(Collections.singletonList(2L), 0, 10))
                .thenReturn(Collections.singletonList(studyResult));
        when(componentResultDao.findIdsByStudyResultId(2L))
                .thenReturn(Collections.singletonList(10L));
        when(componentResultDao.findById(10L)).thenReturn(componentResult);
        when(componentResultDao.getData(10L)).thenReturn("data-10");

        ObjectNode studyResultMetadata = Json.newObject();
        studyResultMetadata.put("id", 2L);
        studyResultMetadata.put("uuid", "study-result-uuid");
        when(domainJsonMapper.studyResultMetadata(studyResult)).thenReturn(studyResultMetadata);

        ObjectNode componentResultMetadata = Json.newObject();
        componentResultMetadata.put("id", 10L);
        componentResultMetadata.put("uuid", "component-result-uuid");
        when(domainJsonMapper.componentResultMetadata(componentResult)).thenReturn(componentResultMetadata);

        // When
        Source<ByteString, ?> source = resultStreamer.streamResults(request, ResultType.COMBINED);
        byte[] zipBytes = materializeToBytes(source);
        Map<String, String> zipEntries = readZipEntries(zipBytes);

        // Then
        assertThat(zipEntries.get("study_result_2/comp-result_10/data.txt")).isEqualTo("data-10");
        assertThat(zipEntries.get("study_result_2/comp-result_10/files/upload.txt"))
                .isEqualTo("uploaded-file-content");
        assertThat(zipEntries.containsKey("metadata.json")).isTrue();

        JsonNode metadata = Json.parse(zipEntries.get("metadata.json"));
        assertThat(metadata.get("data").size()).isEqualTo(1);
        assertThat(metadata.get("data").get(0).get("studyId").asLong()).isEqualTo(1L);
        assertThat(metadata.get("data").get(0).get("studyUuid").asText()).isEqualTo("study-uuid");
        assertThat(metadata.get("data").get(0).get("studyTitle").asText()).isEqualTo("Study title");
        assertThat(metadata.get("data").get(0).get("studyResults").size()).isEqualTo(1);
        assertThat(metadata.get("data").get(0).get("studyResults").get(0).get("id").asLong()).isEqualTo(2L);
        assertThat(metadata.get("data").get(0).get("studyResults").get(0).get("componentResults").size()).isEqualTo(1);
        assertThat(metadata.get("data").get(0).get("studyResults").get(0).get("componentResults").get(0).get("id").asLong())
                .isEqualTo(10L);

        verify(authorizationService).canUserAccessStudy(study, user);
        verify(studyLogger).log(study, user, "Exported results (files and/or data)");
    }

    @Test
    public void streamStudyResultsByStudy_streamsJsonArray() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);

        StudyResult studyResult1 = new StudyResult();
        studyResult1.setId(10L);

        StudyResult studyResult2 = new StudyResult();
        studyResult2.setId(20L);

        when(studyResultDao.countByStudy(study)).thenReturn(2);
        when(studyResultDao.findAllByStudy(eq(study), eq(0), anyInt()))
                .thenReturn(Arrays.asList(studyResult1, studyResult2));

        Map<Long, Integer> counts = new HashMap<>();
        counts.put(10L, 3);
        counts.put(20L, 4);
        doReturn(counts).when(studyResultDao).countComponentResultsForStudyResultIds(anyList());

        when(domainJsonMapper.studyResultAsJsonNode(studyResult1, 3))
                .thenReturn((ObjectNode) Json.parse("{\"id\":10,\"componentResultCount\":3}"));
        when(domainJsonMapper.studyResultAsJsonNode(studyResult2, 4))
                .thenReturn((ObjectNode) Json.parse("{\"id\":20,\"componentResultCount\":4}"));

        // When
        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByStudy(study);
        String streamed = materializeToString(source);

        // Then
        JsonNode json = Json.parse(streamed);
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get(0).get("id").asLong()).isEqualTo(10L);
        assertThat(json.get(0).get("componentResultCount").asInt()).isEqualTo(3);
        assertThat(json.get(1).get("id").asLong()).isEqualTo(20L);
        assertThat(json.get(1).get("componentResultCount").asInt()).isEqualTo(4);
    }

    @Test
    public void streamComponentResults_streamsJsonArray() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);

        Component component = new Component();
        component.setId(2L);
        component.setStudy(study);

        ComponentResult componentResult1 = new ComponentResult();
        componentResult1.setId(10L);

        ComponentResult componentResult2 = new ComponentResult();
        componentResult2.setId(20L);

        when(componentResultDao.countByComponent(component)).thenReturn(2);
        when(componentResultDao.findAllByComponent(component, 0, 10))
                .thenReturn(Arrays.asList(componentResult1, componentResult2));

        when(domainJsonMapper.componentResultAsJsonNode(componentResult1))
                .thenReturn(Json.parse("{\"id\":10}"));
        when(domainJsonMapper.componentResultAsJsonNode(componentResult2))
                .thenReturn(Json.parse("{\"id\":20}"));

        // When
        Source<ByteString, ?> source = resultStreamer.streamComponentResults(component);
        String streamed = materializeToString(source);

        // Then
        JsonNode json = Json.parse(streamed);
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get(0).get("id").asLong()).isEqualTo(10L);
        assertThat(json.get(1).get("id").asLong()).isEqualTo(20L);
    }

    private Http.Request fakeJsonRequest(JsonNode json) {
        return Helpers.fakeRequest()
                .bodyJson(json)
                .build();
    }

    private void setCurrentContextWithSignedinUser(Http.Request request, User user) {
        Context context = new Context(request);
        context.args().put(SIGNEDIN_USER, user);
        Context.setCurrent(context);
    }

    private String materializeToString(Source<ByteString, ?> source) throws Exception {
        return new String(materializeToBytes(source), StandardCharsets.UTF_8);
    }

    private byte[] materializeToBytes(Source<ByteString, ?> source) throws Exception {
        ByteString bytes = source
                .runFold(ByteString.emptyByteString(), ByteString::concat, materializer)
                .toCompletableFuture()
                .get(60, TimeUnit.SECONDS);
        return bytes.toArray();
    }

    private Map<String, String> readZipEntries(byte[] zipBytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = zipIn.read(buffer)) >= 0) {
                    content.write(buffer, 0, length);
                }
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8));
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        return entries;
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

}