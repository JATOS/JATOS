package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Extracts component result IDs from JSON.
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultIdsExtractor {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final ComponentDao componentDao;

    @Inject
    ComponentResultIdsExtractor(ComponentResultDao componentResultDao, StudyResultDao studyResultDao,
            ComponentDao componentDao) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.componentDao = componentDao;
    }

    /**
     * Extracts component result IDs from a JsonNode. It ensures that those component results actually exist in the
     * database. Basically all IDs used in JATOS are allowed, e.g. study IDs or batch IDs. This method looks in the
     * database for the corresponding component result IDs.
     */
    public List<Long> extract(JsonNode json) throws BadRequestException {
        Set<Long> componentResultIds = new HashSet<>();
        Iterator<String> iterator = json.fieldNames();
        while (iterator.hasNext()) {
            String field = iterator.next();
            switch (field) {
                case "studyIds": {
                    List<Long> studyIds = extractIds(json.get("studyIds"));
                    List<Long> componentIds = componentDao.findIdsByStudyIds(studyIds);
                    componentResultIds.addAll(componentResultDao.findIdsByComponentIds(componentIds));
                    break;
                }
                case "componentIds": {
                    List<Long> componentIds = extractIds(json.get("componentIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByComponentIds(componentIds));
                    break;
                }
                case "componentResultIds": {
                    List<Long> crids = extractIds(json.get("componentResultIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByComponentResultIds(crids));
                    break;
                }
                case "studyResultIds": {
                    List<Long> srids = extractIds(json.get("studyResultIds"));
                    componentResultIds.addAll(componentResultDao.findIdsByStudyResultIds(srids));
                    break;
                }
                case "batchIds": {
                    List<Long> batchIds = extractIds(json.get("batchIds"));
                    List<Long> srids = studyResultDao.findIdsByBatchIds(batchIds);
                    componentResultIds.addAll(componentResultDao.findIdsByStudyResultIds(srids));
                    break;
                }
                case "groupIds": {
                    List<Long> groupIds = extractIds(json.get("groupIds"));
                    List<Long> srids = studyResultDao.findIdsByGroupIds(groupIds);
                    componentResultIds.addAll(componentResultDao.findIdsByStudyResultIds(srids));
                    break;
                }
                default: throw new BadRequestException("Unknown field " + field);
            }
        }
        return new ArrayList<>(componentResultIds);
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
