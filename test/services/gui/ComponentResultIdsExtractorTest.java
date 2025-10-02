package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ComponentResultIdsExtractor.
 *
 * @author Kristian Lange
 */
public class ComponentResultIdsExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void extract_fromNullOrEmptyMap_returnsEmpty() throws Exception {
        ComponentResultDao crDao = Mockito.mock(ComponentResultDao.class);
        StudyResultDao srDao = Mockito.mock(StudyResultDao.class);
        ComponentResultIdsExtractor extractor = new ComponentResultIdsExtractor(crDao, srDao);

        assertThat(extractor.extract((Map<String, String[]>) null)).isEmpty();
        assertThat(extractor.extract(Collections.emptyMap())).isEmpty();
    }

    @Test
    public void extract_fromMap_combinesDedupsAndSorts() throws Exception {
        ComponentResultDao crDao = Mockito.mock(ComponentResultDao.class);
        StudyResultDao srDao = Mockito.mock(StudyResultDao.class);
        ComponentResultIdsExtractor extractor = new ComponentResultIdsExtractor(crDao, srDao);

        // Stubs for DAOs
        when(crDao.findIdsByStudyIds(Arrays.asList(10L, 20L)))
                .thenReturn(Arrays.asList(1000L, 1002L, 1001L));
        when(crDao.findIdsByComponentIds(Collections.singletonList(100L)))
                .thenReturn(Arrays.asList(1002L, 1003L)); // contains duplicate 1002
        when(crDao.findIdsByComponentResultIds(Arrays.asList(5L, 7L)))
                .thenReturn(Arrays.asList(5L, 7L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(11L, 12L));
        when(srDao.findIdsByBatchIds(Collections.singletonList(9L)))
                .thenReturn(Arrays.asList(21L, 22L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(Arrays.asList(21L, 22L)))
                .thenReturn(Arrays.asList(210L, 220L));
        when(srDao.findIdsByGroupIds(Collections.singletonList(3L)))
                .thenReturn(Collections.singletonList(31L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(Collections.singletonList(31L)))
                .thenReturn(Collections.singletonList(310L));

        Map<String, String[]> map = new HashMap<>();
        map.put("studyId", new String[]{"10", "20"});
        map.put("componentId", new String[]{"100"});
        map.put("componentResultId", new String[]{"5", "7"});
        map.put("studyResultId", new String[]{"1", "2"});
        map.put("batchId", new String[]{"9"});
        map.put("groupId", new String[]{"3"});
        map.put("unknownKey", new String[]{"should", "be", "ignored"});

        List<Long> result = extractor.extract(map);

        // Expect sorted ascending and without duplicates
        assertThat(result).isEqualTo(Arrays.asList(5L, 7L, 11L, 12L, 210L, 220L, 310L, 1000L, 1001L, 1002L, 1003L));
    }

    @Test(expected = BadRequestException.class)
    public void extract_fromJson_unknownField_throwsBadRequest() throws Exception {
        ComponentResultDao crDao = Mockito.mock(ComponentResultDao.class);
        StudyResultDao srDao = Mockito.mock(StudyResultDao.class);
        ComponentResultIdsExtractor extractor = new ComponentResultIdsExtractor(crDao, srDao);

        ObjectNode node = mapper.createObjectNode();
        node.put("foo", 1);

        extractor.extract(node);
    }

    @Test
    public void extract_fromJson_validFields_combinesDedupsAndSorts() throws Exception {
        ComponentResultDao crDao = Mockito.mock(ComponentResultDao.class);
        StudyResultDao srDao = Mockito.mock(StudyResultDao.class);
        ComponentResultIdsExtractor extractor = new ComponentResultIdsExtractor(crDao, srDao);

        // JSON with various field types. Note: avoid componentUuids due to unrelated code path.
        String jsonStr = "{" +
                "\"studyIds\":[10,20]," +
                "\"studyUuids\":[\"u1\",\"u2\"]," +
                "\"componentIds\":\"1-2, 4\"," +
                "\"componentResultIds\":[5]," +
                "\"studyResultIds\":\"2 - 4,6, 8-8, x, 9-7\"," +
                "\"batchIds\":[9]," +
                "\"groupIds\":2" +
                "}";
        JsonNode node = mapper.readTree(jsonStr);

        // Prepare expected expansions
        List<Long> studyIds = Arrays.asList(10L, 20L);
        List<String> studyUuids = Arrays.asList("u1", "u2");
        List<Long> componentIds = Arrays.asList(1L, 2L, 4L); // expanded from range
        List<Long> componentResultIds = Collections.singletonList(5L);
        List<Long> studyResultIds = Arrays.asList(2L, 3L, 4L, 6L, 8L); // expanded, ignoring invalids
        List<Long> batchIds = Collections.singletonList(9L);
        List<Long> groupIds = Collections.singletonList(2L);

        // Stubs for DAO methods
        when(crDao.findIdsByStudyIds(studyIds)).thenReturn(Arrays.asList(900L, 901L));
        when(crDao.findIdsByStudyUuids(studyUuids)).thenReturn(Collections.singletonList(902L));
        when(crDao.findIdsByComponentIds(componentIds)).thenReturn(Arrays.asList(100L, 101L));
        when(crDao.findIdsByComponentResultIds(componentResultIds)).thenReturn(Collections.singletonList(5L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(studyResultIds)).thenReturn(Arrays.asList(20L, 30L));

        when(srDao.findIdsByBatchIds(batchIds)).thenReturn(Arrays.asList(200L, 201L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(Arrays.asList(200L, 201L)))
                .thenReturn(Arrays.asList(40L, 50L));

        when(srDao.findIdsByGroupIds(groupIds)).thenReturn(Collections.singletonList(300L));
        when(crDao.findOrderedIdsByOrderedStudyResultIds(Collections.singletonList(300L)))
                .thenReturn(Collections.singletonList(60L));

        List<Long> result = extractor.extract(node);

        // Expect sorted ascending and without duplicates
        assertThat(result).isEqualTo(Arrays.asList(5L, 20L, 30L, 40L, 50L, 60L, 100L, 101L, 900L, 901L, 902L));
    }

    @Test
    public void extract_fromJson_nullOrNullNode_returnsEmpty() throws Exception {
        ComponentResultDao crDao = Mockito.mock(ComponentResultDao.class);
        StudyResultDao srDao = Mockito.mock(StudyResultDao.class);
        ComponentResultIdsExtractor extractor = new ComponentResultIdsExtractor(crDao, srDao);

        assertThat(extractor.extract((JsonNode) null)).isEmpty();
        assertThat(extractor.extract(mapper.nullNode())).isEmpty();
    }
}
