package services.gui;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.Worker;
import play.mvc.Controller;
import play.mvc.Http;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Service class that mostly generates Strings from ComponentResult's or StudyResult's
 * result data. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultDataExportService {

    public static final String JQDOWNLOAD_COOKIE_NAME = "fileDownload";
    public static final String JQDOWNLOAD_COOKIE_CONTENT = "true";
    public static final String DATE_FORMAT_FILE = "yyyyMMddHHmmss";
    public static final SimpleDateFormat DATE_FORMATER_FILE = new SimpleDateFormat(
            DATE_FORMAT_FILE);

    private final Checker checker;
    private final ResultService resultService;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyLogger studyLogger;

    @Inject
    ResultDataExportService(Checker checker, ResultService resultService,
            ComponentResultDao componentResultDao, StudyResultDao studyResultDao,
            StudyLogger studyLogger) {
        this.checker = checker;
        this.resultService = resultService;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyLogger = studyLogger;
    }

    /**
     * Retrieves all StudyResults that belong to the given worker and that the
     * given user is allowed to see (means StudyResults from studies he is a
     * user of), checks them and returns all their result data in one string.
     */
    public String forWorker(User user, Worker worker)
            throws ForbiddenException, BadRequestException {
        List<StudyResult> allowedStudyResultList =
                resultService.getAllowedStudyResultList(user, worker);
        checker.checkStudyResults(allowedStudyResultList, user, false);
        String resultDataAsStr = resultService.studyResultDataToString(allowedStudyResultList);
        studyLogger.logStudyResultDataExporting(allowedStudyResultList, resultDataAsStr);
        return resultDataAsStr;
    }

    /**
     * Retrieves all StudyResults of the given study, checks them and returns
     * all their result data in one string.
     */
    public String forStudy(User user, Study study) throws ForbiddenException, BadRequestException {
        List<StudyResult> studyResultList = studyResultDao.findAllByStudy(study);
        checker.checkStudyResults(studyResultList, user, false);
        String resultDataAsStr = resultService.studyResultDataToString(studyResultList);
        studyLogger.logStudyResultDataExporting(studyResultList, resultDataAsStr);
        return resultDataAsStr;
    }

    /**
     * Retrieves all ComponentResults of the given component, checks them and
     * returns them all their result data in one string.
     */
    public String forComponent(User user, Component component)
            throws ForbiddenException, BadRequestException {
        List<ComponentResult> componentResultList =
                componentResultDao.findAllByComponent(component);
        checker.checkComponentResults(componentResultList, user, false);
        String resultDataAsStr = resultService.componentResultDataToString(componentResultList);
        studyLogger.logComponentResultDataExporting(componentResultList, resultDataAsStr);
        return resultDataAsStr;
    }

    /**
     * Retrieves the StudyResults that correspond to the IDs, checks them and
     * returns all their result data in one string.
     */
    public String fromListOfStudyResultIds(String studyResultIds, User user)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<Long> studyResultIdList = resultService.extractResultIds(studyResultIds);
        List<StudyResult> studyResultList = resultService.getStudyResults(studyResultIdList);
        checker.checkStudyResults(studyResultList, user, false);
        String resultDataAsStr = resultService.studyResultDataToString(studyResultList);
        studyLogger.logStudyResultDataExporting(studyResultList, resultDataAsStr);
        return resultDataAsStr;
    }

    /**
     * Retrieves the ComponentResults that correspond to the IDs, checks them
     * and returns all their result data in one string.
     */
    public String fromListOfComponentResultIds(String componentResultIds, User user)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<Long> componentResultIdList = resultService.extractResultIds(componentResultIds);
        List<ComponentResult> componentResultList =
                resultService.getComponentResults(componentResultIdList);
        checker.checkComponentResults(componentResultList, user, false);
        String resultDataAsStr = resultService.componentResultDataToString(componentResultList);
        studyLogger.logComponentResultDataExporting(componentResultList, resultDataAsStr);
        return resultDataAsStr;
    }

    /**
     * Prepares the response for a download with the johnculviner's
     * jQuery.fileDownload plugin. This plugin is merely used to detect a failed
     * download. If the response isn't OK and it doesn't have this cookie then
     * the plugin regards it as a fail.
     */
    private void prepareResponseForExport() {
        String dateForFile = DATE_FORMATER_FILE.format(new Date());
        String filename = "results_" + dateForFile + "."
                + IOUtils.TXT_FILE_SUFFIX;
        Controller.response().setHeader("Content-disposition", "attachment; filename=" + filename);
        // Set transient cookie with no domain or path constraints
        Http.Cookie cookie = new Http.Cookie(JQDOWNLOAD_COOKIE_NAME,
                JQDOWNLOAD_COOKIE_CONTENT, null, "/", null, false, false);
        Controller.response().setCookie(cookie);
    }

}
