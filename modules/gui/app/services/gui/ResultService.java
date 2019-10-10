package services.gui;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
