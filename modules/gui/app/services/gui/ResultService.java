package services.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;

    @Inject
    ResultService(ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
    }

    /**
     * Parses a String with result IDs and returns them in a List<Long>. Throws an BadRequestException if an ID is not a
     * number or if the original String doesn't contain any ID.
     */
    public List<Long> extractResultIds(String resultIds) throws BadRequestException {
        String[] resultIdStrArray = resultIds.split(",");
        List<Long> resultIdList = new ArrayList<>();
        for (String idStr : resultIdStrArray) {
            try {
                if (idStr.trim().isEmpty()) {
                    continue;
                }
                resultIdList.add(Long.parseLong(idStr.trim()));
            } catch (NumberFormatException e) {
                throw new BadRequestException(MessagesStrings.resultIdMalformed(idStr));
            }
        }
        if (resultIdList.size() < 1) {
            throw new BadRequestException(MessagesStrings.NO_RESULTS_SELECTED);
        }
        return resultIdList;
    }

    /**
     * Gets the corresponding ComponentResult for a list of IDs. Throws an exception if the ComponentResult doesn't
     * exist.
     */
    public List<ComponentResult> getComponentResults(List<Long> componentResultIdList) throws NotFoundException {
        List<ComponentResult> componentResultList = new ArrayList<>();
        for (Long componentResultId : componentResultIdList) {
            ComponentResult componentResult = componentResultDao.findById(componentResultId);
            if (componentResult == null) {
                throw new NotFoundException(MessagesStrings.componentResultNotExist(componentResultId));
            }
            componentResultList.add(componentResult);
        }
        return componentResultList;
    }

    /**
     * Get all StudyResults or throw an Exception if one doesn't exist. Throws an exception if the StudyResult doesn't
     * exist.
     */
    public List<StudyResult> getStudyResults(List<Long> studyResultIdList) throws NotFoundException {
        List<StudyResult> studyResultList = new ArrayList<>();
        for (Long studyResultId : studyResultIdList) {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            if (studyResult == null) {
                throw new NotFoundException(MessagesStrings.studyResultNotExist(studyResultId));
            }
            studyResultList.add(studyResult);
        }
        return studyResultList;
    }

    /**
     * Generate the list of StudyResults that belong to the given Worker and that the given user is allowed to see. A
     * user is allowed if the study that the StudyResult belongs too has this user.
     */
    public List<StudyResult> getAllowedStudyResultList(User user, Worker worker) {
        // Check for studyResult != null should not be necessary but it lead to an NPE at least once
        return worker.getStudyResultList().stream().filter(
                studyResult -> studyResult != null && studyResult.getStudy().hasUser(user)).collect(
                Collectors.toList());
    }

}
