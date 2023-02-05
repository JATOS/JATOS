package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Extracts component result IDs from JSON or a Map.
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultIdsExtractor {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;

    @Inject
    ComponentResultIdsExtractor(ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
    }

    /**
     * Extracts component result IDs from the given map. It ensures that those component results actually exist in the
     * database. Basically all IDs used in JATOS are allowed, e.g. study IDs or batch IDs. This method looks in the
     * database for the corresponding component result IDs.
     */
    public List<Long> extract(Map<String, String[]> map) throws BadRequestException {
        if (map == null || map.isEmpty()) return new ArrayList<>();

        Set<Long> componentResultIds = new HashSet<>();
        for (String field : map.keySet()) {
            switch (field) {
                case "studyId": {
                    List<Long> studyIds = Arrays.stream(map.get("studyId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findIdsByStudyIds(studyIds));
                    break;
                }
                case "studyUuid": {
                    List<String> studyUuids = Arrays.stream(map.get("studyUuid"))
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findIdsByStudyUuids(studyUuids));
                    break;
                }
                case "componentId": {
                    List<Long> componentIds = Arrays.stream(map.get("componentId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findIdsByComponentIds(componentIds));
                    break;
                }
                case "componentUuid": {
                    List<String> componentUuids = Arrays.stream(map.get("componentUuid"))
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findIdsByComponentUuids(componentUuids));
                    break;
                }
                case "componentResultId": {
                    List<Long> crids = Arrays.stream(map.get("componentResultId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findIdsByComponentResultIds(crids));
                    break;
                }
                case "studyResultId": {
                    List<Long> srids = Arrays.stream(map.get("studyResultId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                case "batchId": {
                    List<Long> batchIds = Arrays.stream(map.get("batchId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    List<Long> srids = studyResultDao.findIdsByBatchIds(batchIds);
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                case "groupId": {
                    List<Long> groupIds = Arrays.stream(map.get("groupId"))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    List<Long> srids = studyResultDao.findIdsByGroupIds(groupIds);
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                default:
                    throw new BadRequestException("Unknown field " + field);
            }
        }
        return new ArrayList<>(componentResultIds);
    }

    /**
     * Extracts component result IDs from a JsonNode. It ensures that those component results actually exist in the
     * database. Basically all IDs used in JATOS are allowed, e.g. study IDs or batch IDs. This method looks in the
     * database for the corresponding component result IDs.
     */
    public List<Long> extract(JsonNode json) throws BadRequestException {
        if (json == null || json.isNull()) return new ArrayList<>();

        Set<Long> componentResultIds = new HashSet<>();
        Iterator<String> iterator = json.fieldNames();
        while (iterator.hasNext()) {
            String field = iterator.next();
            switch (field) {
                case "studyIds": {
                    List<Long> studyIds = extractIds(json.get("studyIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByStudyIds(studyIds));
                    break;
                }
                case "studyUuids": {
                    List<String> studyUuids = extractUuids(json.get("studyUuids"));
                    componentResultIds.addAll(componentResultDao.findIdsByStudyUuids(studyUuids));
                    break;
                }
                case "componentIds": {
                    List<Long> componentIds = extractIds(json.get("componentIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByComponentIds(componentIds));
                    break;
                }
                case "componentUuids": {
                    List<String> componentUuids = extractUuids(json.get("studyUuids"));
                    componentResultIds.addAll(componentResultDao.findIdsByComponentUuids(componentUuids));
                    break;
                }
                case "componentResultIds": {
                    List<Long> crids = extractIds(json.get("componentResultIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByComponentResultIds(crids));
                    break;
                }
                case "studyResultIds": {
                    List<Long> srids = extractIds(json.get("studyResultIds"));
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                case "batchIds": {
                    List<Long> batchIds = extractIds(json.get("batchIds"));
                    List<Long> srids = studyResultDao.findIdsByBatchIds(batchIds);
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                case "groupIds": {
                    List<Long> groupIds = extractIds(json.get("groupIds"));
                    List<Long> srids = studyResultDao.findIdsByGroupIds(groupIds);
                    componentResultIds.addAll(componentResultDao.findOrderedIdsByOrderedStudyResultIds(srids));
                    break;
                }
                default:
                    throw new BadRequestException("Unknown field " + field);
            }
        }
        return new ArrayList<>(componentResultIds);
    }

    private static List<String> extractUuids(JsonNode node) throws BadRequestException {
        if (node.isTextual()) {
            return Collections.singletonList(node.asText());
        } else if (node.isArray()) {
            List<String> uuids = new ArrayList<>();
            node.forEach(n -> uuids.add(n.asText()));
            return uuids;
        } else if (node.isNull()) {
            return new ArrayList<>();
        } else {
            throw new BadRequestException("Malformed JSON");
        }
    }

    private static List<Long> extractIds(JsonNode node) throws BadRequestException {
        if (node.isInt()) {
            return Collections.singletonList(node.asLong());
        } else if (node.isArray()) {
            List<Long> ids = new ArrayList<>();
            node.forEach(n -> ids.add(n.asLong()));
            return ids;
        } else if (node.isTextual()) {
            return extractIdsFromText(node.asText());
        } else if (node.isNull()) {
            return new ArrayList<>();
        } else {
            throw new BadRequestException("Malformed JSON");
        }
    }

    private static List<Long> extractIdsFromText(String text) {
        List<Long> ids = new ArrayList<>();
        String[] elements = text.split(",");
        elements = StringUtils.stripAll(elements);
        for (String element : elements) {
            Matcher matcher = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$").matcher(element);
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(1));
                int max = Integer.parseInt(matcher.group(2));
                IntStream.rangeClosed(min, max).forEach(id -> ids.add((long) id));
            } else if (Pattern.matches("^\\s*\\d+\\s*$", element)) {
                ids.add(Long.parseLong(element));
            }
        }
        return ids;
    }

}
