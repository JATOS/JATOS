package services.gui;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import general.common.Common;
import general.common.StudyLogger;
import json.common.DomainJsonMapper;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import org.junit.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.libs.Json;
import services.gui.ResultStreamer.ResultType;
import testutils.gui.JPAMocker;

import javax.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the synchronous writer logic in ResultStreamer.
 */
public class ResultStreamerTest {

    private static MockedStatic<Common> commonStatic;

    private ComponentResultDao componentResultDao;
    private StudyResultDao studyResultDao;
    private StudyDao studyDao;
    private DomainJsonMapper domainJsonMapper;
    private AuthorizationService authorizationService;
    private StudyLogger studyLogger;

    private ResultStreamer resultStreamer;

    private User user;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void initCommonStatics() {
        commonStatic = Mockito.mockStatic(Common.class);
        commonStatic.when(Common::getMaxResultsDbQuerySize).thenReturn(10);
    }

    @AfterClass
    public static void tearDownCommonStatics() {
        if (commonStatic != null) commonStatic.close();
    }


    @Before
    public void setUp() {
        componentResultDao = mock(ComponentResultDao.class);
        studyResultDao = mock(StudyResultDao.class);
        studyDao = mock(StudyDao.class);
        domainJsonMapper = mock(DomainJsonMapper.class);
        authorizationService = mock(AuthorizationService.class);
        studyLogger = mock(StudyLogger.class);
        ComponentResultIdsExtractor componentResultIdsExtractor = mock(ComponentResultIdsExtractor.class);

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

        //noinspection unchecked
        when(studyResultDao.withReadOnlyTransaction(any(Function.class))).thenAnswer(invocation -> {
            Function<EntityManager, Object> function = invocation.getArgument(0);
            return function.apply(null);
        });

        JPAMocker.mockDaoTransactions(studyResultDao, componentResultDao, studyDao);
    }

    @Test
    public void writeComponentResultData_writesDataWithLineSeparator() {
        // Given
        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(11L);
        when(componentResultDao.getData(11L)).thenReturn("result data");

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeComponentResultData(writer, componentResult);

        // Then
        assertThat(writer.toString()).isEqualTo("result data" + System.lineSeparator());
        verify(componentResultDao).getData(11L);
    }

    @Test
    public void writeComponentResultData_withNullDataWritesNothing() {
        // Given
        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(11L);
        when(componentResultDao.getData(11L)).thenReturn(null);

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeComponentResultData(writer, componentResult);

        // Then
        assertThat(writer.toString()).isEmpty();
        verify(componentResultDao).getData(11L);
    }

    @Test
    public void writeComponentResultDataByIds_writesExistingComponentResultsAndLogsStudies() {
        // Given
        Study study = new Study();
        study.setId(1L);

        StudyResult studyResult = new StudyResult();
        studyResult.setId(2L);
        studyResult.setStudy(study);

        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(3L);
        componentResult.setStudyResult(studyResult);

        when(componentResultDao.findById(3L)).thenReturn(componentResult);
        when(componentResultDao.findById(99L)).thenReturn(null);
        when(componentResultDao.getData(3L)).thenReturn("data-3");

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeComponentResultDataByIds(writer, Arrays.asList(3L, 99L), user);

        // Then
        assertThat(writer.toString()).isEqualTo("data-3" + System.lineSeparator());

        verify(componentResultDao).findById(3L);
        verify(componentResultDao).findById(99L);
        verify(authorizationService).canUserAccessComponentResult(componentResult, user, false);
        verify(studyLogger).log(study, user, "Exported result data to file");
    }

    @Test
    public void writeComponentResults_writesJsonArrayItemsWithCommaWhenNotLastPage() {
        // Given
        ComponentResult componentResult1 = new ComponentResult();
        componentResult1.setId(1L);

        ComponentResult componentResult2 = new ComponentResult();
        componentResult2.setId(2L);

        when(domainJsonMapper.componentResultAsJsonNode(componentResult1))
                .thenReturn(Json.parse("{\"id\":1}"));
        when(domainJsonMapper.componentResultAsJsonNode(componentResult2))
                .thenReturn(Json.parse("{\"id\":2}"));

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeComponentResults(writer, false, Arrays.asList(componentResult1, componentResult2));

        // Then
        assertThat(writer.toString()).isEqualTo("{\"id\":1},\n{\"id\":2},\n");
    }

    @Test
    public void writeComponentResults_doesNotWriteTrailingCommaOnLastPage() {
        // Given
        ComponentResult componentResult1 = new ComponentResult();
        componentResult1.setId(1L);

        ComponentResult componentResult2 = new ComponentResult();
        componentResult2.setId(2L);

        when(domainJsonMapper.componentResultAsJsonNode(componentResult1))
                .thenReturn(Json.parse("{\"id\":1}"));
        when(domainJsonMapper.componentResultAsJsonNode(componentResult2))
                .thenReturn(Json.parse("{\"id\":2}"));

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeComponentResults(writer, true, Arrays.asList(componentResult1, componentResult2));

        // Then
        assertThat(writer.toString()).isEqualTo("{\"id\":1},\n{\"id\":2}");
    }

    @Test
    public void writeStudyResults_writesComponentResultCountsIntoMappedJson() {
        // Given
        StudyResult studyResult1 = new StudyResult();
        studyResult1.setId(1L);

        StudyResult studyResult2 = new StudyResult();
        studyResult2.setId(2L);

        Map<Long, Integer> counts = new HashMap<>();
        counts.put(1L, 3);
        counts.put(2L, 4);

        when(studyResultDao.countComponentResultsForStudyResultIds(Arrays.asList(1L, 2L))).thenReturn(counts);
        when(domainJsonMapper.studyResultAsJsonNode(studyResult1, 3))
                .thenReturn((ObjectNode) Json.parse("{\"id\":1,\"componentResultCount\":3}"));
        when(domainJsonMapper.studyResultAsJsonNode(studyResult2, 4))
                .thenReturn((ObjectNode) Json.parse("{\"id\":2,\"componentResultCount\":4}"));

        StringWriter writer = new StringWriter();

        // When
        resultStreamer.writeStudyResults(writer, true, Arrays.asList(studyResult1, studyResult2));

        // Then
        assertThat(writer.toString()).isEqualTo(
                "{\"id\":1,\"componentResultCount\":3},\n" +
                        "{\"id\":2,\"componentResultCount\":4}");
    }

    @Test
    public void writeComponentResultsToZip_dataOnlyAddsDataTxtEntry() throws Exception {
        // Given
        when(componentResultDao.getData(10L)).thenReturn("some result data");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // When
        try (ZipOutputStream zipOut = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            resultStreamer.writeComponentResultsToZip(
                    5L,
                    Collections.singletonList(10L),
                    zipOut,
                    ResultType.DATA_ONLY);
        }

        // Then
        ZipEntryAndContent entry = readSingleZipEntry(bytes.toByteArray());

        assertThat(entry.name).isEqualTo("study_result_5/comp-result_10/data.txt");
        assertThat(entry.content).isEqualTo("some result data");
        verify(componentResultDao).getData(10L);
    }

    @Test
    public void writeComponentResultsToZip_metadataOnlyReturnsComponentResultMetadata() {
        // Given
        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(10L);

        ObjectNode metadata = Json.newObject();
        metadata.put("id", 10L);
        metadata.put("data", "metadata");

        when(componentResultDao.findById(10L)).thenReturn(componentResult);
        when(domainJsonMapper.componentResultMetadata(componentResult)).thenReturn(metadata);

        // When
        ArrayNode result = resultStreamer.writeComponentResultsToZip(
                5L,
                Collections.singletonList(10L),
                null,
                ResultType.METADATA_ONLY);

        // Then
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).get("id").asLong()).isEqualTo(10L);
        assertThat(result.get(0).get("data").asText()).isEqualTo("metadata");
        verify(componentResultDao).findById(10L);
        verify(domainJsonMapper).componentResultMetadata(componentResult);
    }

    @Test
    public void writeResults_metadataOnlyCreatesMetadataFileAndAuthorizesStudy() throws Exception {
        // Given
        Study study = new Study();
        study.setId(1L);
        study.setUuid("study-uuid");
        study.setTitle("Study title");

        StudyResult studyResult = new StudyResult();
        studyResult.setId(2L);

        ComponentResult componentResult = new ComponentResult();
        componentResult.setId(3L);

        when(studyResultDao.findIdsByComponentResultIds(Collections.singletonList(3L)))
                .thenReturn(Collections.singletonList(2L));
        when(studyDao.findIdsByStudyResultIds(Collections.singletonList(2L)))
                .thenReturn(Collections.singletonList(1L));
        when(studyDao.findById(1L)).thenReturn(study);
        when(studyResultDao.findIdsFromListThatBelongToStudy(Collections.singletonList(2L), 1L))
                .thenReturn(Collections.singletonList(2L));
        when(studyResultDao.findByIds(eq(Collections.singletonList(2L)), eq(0), anyInt()))
                .thenReturn(Collections.singletonList(studyResult));
        when(componentResultDao.findIdsByStudyResultId(2L))
                .thenReturn(Collections.singletonList(3L));
        when(componentResultDao.findById(3L)).thenReturn(componentResult);

        ObjectNode studyResultMetadata = Json.newObject();
        studyResultMetadata.put("id", 2L);
        when(domainJsonMapper.studyResultMetadata(studyResult)).thenReturn(studyResultMetadata);

        ObjectNode componentResultMetadata = Json.newObject();
        componentResultMetadata.put("id", 3L);
        when(domainJsonMapper.componentResultMetadata(componentResult)).thenReturn(componentResultMetadata);

        // When
        Path metadataFile = resultStreamer.writeResults(
                Collections.singletonList(3L),
                user,
                null,
                ResultType.METADATA_ONLY,
                Collections.emptyMap());

        // Then
        String metadataJson = Files.readString(metadataFile);

        assertThat(Json.parse(metadataJson).get("data").size()).isEqualTo(1);
        assertThat(Json.parse(metadataJson).get("data").get(0).get("studyId").asLong()).isEqualTo(1L);
        assertThat(Json.parse(metadataJson).get("data").get(0).get("studyUuid").asText()).isEqualTo("study-uuid");
        assertThat(Json.parse(metadataJson).get("data").get(0).get("studyTitle").asText()).isEqualTo("Study title");
        assertThat(Json.parse(metadataJson).get("data").get(0).get("studyResults").size()).isEqualTo(1);
        assertThat(Json.parse(metadataJson).get("data").get(0).get("studyResults").get(0).get("componentResults").size()).isEqualTo(1);

        verify(authorizationService).canUserAccessStudy(study, user);
        verifyNoInteractions(studyLogger);

        Files.deleteIfExists(metadataFile);
    }

    private ZipEntryAndContent readSingleZipEntry(byte[] zipBytes) throws Exception {
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipIn.getNextEntry();
            assertThat(entry).isNotNull();

            ByteArrayOutputStream content = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = zipIn.read(buffer)) >= 0) {
                content.write(buffer, 0, length);
            }

            assertThat(zipIn.getNextEntry()).isNull();

            Assert.assertNotNull(entry);
            return new ZipEntryAndContent(
                    entry.getName(),
                    content.toString(StandardCharsets.UTF_8));
        }
    }

    private static class ZipEntryAndContent {

        private final String name;
        private final String content;

        private ZipEntryAndContent(String name, String content) {
            this.name = name;
            this.content = content;
        }

    }

}