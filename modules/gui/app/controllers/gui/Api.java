package controllers.gui;

import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import general.common.RequestScope;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static controllers.gui.actionannotations.ApiTokenAuthAction.API_TOKEN;
import static controllers.gui.actionannotations.ApiTokenAuthAction.ApiTokenAuth;
import static models.common.User.Role.ADMIN;

/**
 * JATOS API Controller: interface for all requests possible via JATOS' API
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Api extends Controller {

    private final Admin admin;
    private final Studies studies;
    private final StudyLinks studyLinks;
    private final ImportExport importExport;
    private final StudyResults studyResults;
    private final ComponentResults componentResults;

    @Inject
    Api(Admin admin, Studies studies, StudyLinks studyLinks, ImportExport importExport, StudyResults studyResults,
            ComponentResults componentResults) {
        this.admin = admin;
        this.studies = studies;
        this.studyLinks = studyLinks;
        this.importExport = importExport;
        this.studyResults = studyResults;
        this.componentResults = componentResults;
    }

    @ApiTokenAuth
    public Result testToken() {
        return ok(JsonUtils.asJson(RequestScope.get(API_TOKEN)));
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result status() {
        return admin.status();
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result logs(String filename) {
        return admin.logs(filename, -1, false);
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result studyAssetsSize(Long studyId) {
        return admin.studyAssetsSize(studyId);
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result resultDataSize(Long studyId) {
        return admin.resultDataSize(studyId);
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result resultFileSize(Long studyId) {
        return admin.resultFileSize(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyLog(Long studyId) {
        return studies.studyLog(studyId, -1, true);
    }

    @Transactional
    @ApiTokenAuth
    public Result allWorkersByStudy(Long studyId) {
        return studies.allWorkers(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result createStudyCodes(Long studyId, Long batchId, String workerType, String comment, Integer amount) {
        return studyLinks.createStudyCodes(studyId, batchId, workerType, comment, amount);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportStudy(Long studyId) {
        return importExport.exportStudy(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result importStudy(Http.Request request, boolean overwriteProperties, boolean overwriteDir,
            boolean keepCurrentDirName, boolean renameDir) {
        return importExport.importStudyApi(request, overwriteProperties, overwriteDir, keepCurrentDirName, renameDir);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyResultsOverviewByStudy(Long studyId) {
        return studyResults.tableDataByStudy(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyResultsOverviewByWorker(Long workerId) {
        return studyResults.tableDataByWorker(workerId);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyResultsOverviewByBatch(Long studyId, Long batchId, Option<String> workerType) {
        return studyResults.tableDataByBatch(studyId, batchId, workerType);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyResultsOverviewByGroup(Long studyId, Long groupResultId) {
        return studyResults.tableDataByGroup(studyId, groupResultId);
    }

    @Transactional
    @ApiTokenAuth
    public Result componentResultsOverviewByStudyAndComponent(Long studyId, Long componentId) {
        return componentResults.tableDataByComponent(studyId, componentId);
    }

    @Transactional
    @ApiTokenAuth
    public Result componentResultData(Long componentResultId) {
        return componentResults.tableDataComponentResultData(componentResultId);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportDataOfStudyResults(Http.Request request) throws IOException {
        return importExport.exportDataOfStudyResults(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResultFilesOfStudyResults(Http.Request request) throws IOException {
        return importExport.exportResultFilesOfStudyResults(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportDataOfComponentResults(Http.Request request) {
        return importExport.exportDataOfComponentResults(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResultFilesOfComponentResults(Http.Request request) throws IOException {
        return importExport.exportResultFilesOfComponentResults(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result downloadSingleResultFile(Long studyId, Long studyResultId, Long componetResultId, String filename) {
        return importExport.downloadSingleResultFile(studyId, studyResultId, componetResultId, filename);
    }

    @Transactional
    @ApiTokenAuth
    public Result removeStudyResults(Http.Request request) {
        return studyResults.remove(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result removeComponentResults(Http.Request request) {
        return componentResults.remove(request);
    }

}
